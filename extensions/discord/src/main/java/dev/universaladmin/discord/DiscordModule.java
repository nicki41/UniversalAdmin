package dev.universaladmin.discord;

import dev.universaladmin.audit.AuditEvent;
import dev.universaladmin.command.BaseAdminCommand;
import dev.universaladmin.command.CommandInvocation;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.module.GuiIcon;
import dev.universaladmin.module.Module;
import dev.universaladmin.module.ModuleContext;
import dev.universaladmin.module.ModuleDescriptor;
import dev.universaladmin.module.ModuleId;
import dev.universaladmin.permission.PermissionDefault;
import dev.universaladmin.permission.PermissionDefinition;
import dev.universaladmin.permission.PermissionNode;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * Subscribes to UniversalAdmin's audit stream and posts every matching event
 * to the configured Discord webhook, plus {@code /admin discord
 * <status|test|reload>}. Everything is registered through the
 * {@link ModuleContext}, so disabling the plugin removes it again.
 */
final class DiscordModule implements Module {

    static final ModuleId ID = ModuleId.of("discord", "webhook");
    static final PermissionNode MANAGE = PermissionNode.of("universaladmin.discord.manage");

    private static final ModuleDescriptor DESCRIPTOR = ModuleDescriptor.builder(ID, "Discord")
            .description("Posts punishments, reports and other admin actions to a Discord channel.")
            .icon(new GuiIcon("bell", "Discord"))
            .build();

    private final Supplier<DiscordConfig> config;
    private final Runnable reload;
    private final WebhookClient client;

    DiscordModule(Supplier<DiscordConfig> config, Runnable reload, WebhookClient client) {
        this.config = config;
        this.reload = reload;
        this.client = client;
    }

    @Override
    public ModuleDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public void onEnable(ModuleContext context) {
        context.registerPermission(new PermissionDefinition(MANAGE, "Test and reload the Discord integration", PermissionDefault.OP));
        context.subscribeAudit(this::onAudit);
        context.registerCommand(new DiscordCommand());
        if (!config.get().configured()) {
            context.logger().warning("No valid webhook-url in plugins/UniversalAdminDiscord/config.yml yet - nothing is posted "
                    + "until one is set (then run /admin discord reload).");
        }
    }

    private void onAudit(AuditEvent event) {
        DiscordConfig current = config.get();
        if (!current.configured()
                || (!event.success() && !current.includeFailures())
                || !current.filter().matches(event.type().toString())) {
            return;
        }
        client.send(current.webhookUrl(), EmbedFormatter.payload(event, current));
    }

    /** {@code /admin discord <status|test|reload>}. */
    final class DiscordCommand extends BaseAdminCommand {

        DiscordCommand() {
            super("discord", MANAGE, MessageKey.of("discord.command.usage"), MessageKey.of("discord.command.description"));
        }

        @Override
        public void execute(CommandInvocation invocation) {
            String sub = invocation.arg(0).map(arg -> arg.toLowerCase(Locale.ROOT)).orElse("status");
            switch (sub) {
                case "reload" -> {
                    reload.run();
                    invocation.reply(config.get().configured() ? "discord.reloaded" : "discord.not-configured");
                }
                case "test" -> {
                    DiscordConfig current = config.get();
                    if (!current.configured()) {
                        invocation.reply("discord.not-configured");
                        return;
                    }
                    invocation.reply("discord.test-sending");
                    client.send(current.webhookUrl(), EmbedFormatter.testPayload(current, invocation.actor().displayName()))
                            .thenAccept(status -> invocation.reply(status >= 200 && status < 300 ? "discord.test-ok" : "discord.test-failed",
                                    String.valueOf(status)));
                }
                default -> {
                    DiscordConfig current = config.get();
                    invocation.reply(current.configured() ? "discord.status-on" : "discord.not-configured",
                            String.join(", ", current.events()));
                }
            }
        }

        @Override
        public List<String> complete(CommandInvocation invocation) {
            return invocation.argCount() <= 1 ? List.of("status", "test", "reload") : List.of();
        }
    }
}

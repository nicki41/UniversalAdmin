package dev.universaladmin.discord;

import dev.universaladmin.audit.AuditEvent;
import dev.universaladmin.command.BaseAdminCommand;
import dev.universaladmin.command.CommandInvocation;
import dev.universaladmin.core.UniversalAdmin;
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
import java.util.logging.Logger;

/**
 * Posts UniversalAdmin's admin actions to Discord - through a webhook, or
 * (bot mode) through {@link DiscordBot}, which also bridges the staff chat,
 * offers slash commands and report buttons - plus {@code /admin discord
 * <status|test|reload>}. Everything is registered through the
 * {@link ModuleContext}, so disabling the plugin removes it again.
 */
final class DiscordModule implements Module {

    static final ModuleId ID = ModuleId.of("discord", "webhook");
    static final PermissionNode MANAGE = PermissionNode.of("universaladmin.discord.manage");

    private static final ModuleDescriptor DESCRIPTOR = ModuleDescriptor.builder(ID, "Discord")
            .description("Posts admin actions to Discord; as a bot also staff chat, slash commands and report buttons.")
            .icon(new GuiIcon("bell", "Discord"))
            .build();

    private final Supplier<DiscordConfig> config;
    private final Runnable reload;
    private final WebhookClient client;
    private UniversalAdmin universalAdmin;
    private Logger logger;
    private volatile DiscordBot bot;

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
        universalAdmin = context.platform();
        logger = context.logger();
        context.registerPermission(new PermissionDefinition(MANAGE, "Test and reload the Discord integration", PermissionDefault.OP));
        context.subscribeAudit(this::onAudit);
        context.registerCommand(new DiscordCommand());
        context.resources().closeable(this::stopBot);
        applyMode();
    }

    /** Starts or stops the bot to match the configuration, and says what is missing. */
    private synchronized void applyMode() {
        stopBot();
        DiscordConfig current = config.get();
        if (current.mode() == DiscordConfig.Mode.BOT) {
            if (!current.bot().configured()) {
                logger.warning("mode is \"bot\", but bot.token or bot.guild-id is missing in plugins/UniversalAdminDiscord/config.yml "
                        + "- see docs/extensions/discord.md, then /admin discord reload.");
                return;
            }
            DiscordBot started = new DiscordBot(universalAdmin, logger);
            started.start(current);
            bot = started;
        } else if (!current.configured()) {
            logger.warning("No valid webhook-url in plugins/UniversalAdminDiscord/config.yml yet - nothing is posted "
                    + "until one is set (then run /admin discord reload).");
        }
    }

    private synchronized void stopBot() {
        DiscordBot running = bot;
        bot = null;
        if (running != null) {
            running.stop();
        }
    }

    private void onAudit(AuditEvent event) {
        DiscordConfig current = config.get();
        if (current.mode() == DiscordConfig.Mode.BOT) {
            DiscordBot running = bot;
            if (running != null) {
                running.onAudit(event);
            }
            return;
        }
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
            DiscordConfig current = config.get();
            switch (sub) {
                case "reload" -> {
                    reload.run();
                    applyMode();
                    DiscordConfig reloaded = config.get();
                    invocation.reply(reloaded.mode() == DiscordConfig.Mode.BOT
                            ? (reloaded.bot().configured() ? "discord.reloaded-bot" : "discord.bot-not-configured")
                            : (reloaded.configured() ? "discord.reloaded" : "discord.not-configured"));
                }
                case "test" -> {
                    if (current.mode() == DiscordConfig.Mode.BOT) {
                        DiscordBot running = bot;
                        if (running == null) {
                            invocation.reply("discord.bot-not-configured");
                            return;
                        }
                        invocation.reply("discord.test-sending");
                        running.sendTest(invocation.actor().displayName())
                                .thenAccept(ok -> invocation.reply(ok ? "discord.test-ok" : "discord.test-bot-failed", running.state()));
                        return;
                    }
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
                    if (current.mode() == DiscordConfig.Mode.BOT) {
                        DiscordBot running = bot;
                        invocation.reply(running == null ? "discord.bot-not-configured" : "discord.status-bot",
                                running == null ? "" : running.state());
                    } else {
                        invocation.reply(current.configured() ? "discord.status-on" : "discord.not-configured",
                                String.join(", ", current.events()));
                    }
                }
            }
        }

        @Override
        public List<String> complete(CommandInvocation invocation) {
            return invocation.argCount() <= 1 ? List.of("status", "test", "reload") : List.of();
        }
    }
}

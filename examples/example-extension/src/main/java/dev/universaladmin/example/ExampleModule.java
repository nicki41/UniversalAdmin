package dev.universaladmin.example;

import dev.universaladmin.action.Action;
import dev.universaladmin.action.ActionContext;
import dev.universaladmin.action.ActionDefinition;
import dev.universaladmin.action.ActionId;
import dev.universaladmin.action.ActionResult;
import dev.universaladmin.action.ActionTarget;
import dev.universaladmin.command.Arguments;
import dev.universaladmin.command.BaseAdminCommand;
import dev.universaladmin.command.CommandInvocation;
import dev.universaladmin.core.PlayerRef;
import dev.universaladmin.core.UniversalAdmin;
import dev.universaladmin.gui.AbstractGuiPage;
import dev.universaladmin.gui.GuiActions;
import dev.universaladmin.gui.GuiButton;
import dev.universaladmin.gui.GuiClickContext;
import dev.universaladmin.gui.GuiItem;
import dev.universaladmin.gui.GuiLayout;
import dev.universaladmin.gui.GuiPageId;
import dev.universaladmin.gui.GuiRenderContext;
import dev.universaladmin.gui.PlayerMenuEntry;
import dev.universaladmin.localization.ComponentMessages;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.localization.MessageService;
import dev.universaladmin.module.GuiIcon;
import dev.universaladmin.module.Module;
import dev.universaladmin.module.ModuleContext;
import dev.universaladmin.module.ModuleDescriptor;
import dev.universaladmin.module.ModuleId;
import dev.universaladmin.permission.PermissionDefault;
import dev.universaladmin.permission.PermissionDefinition;
import dev.universaladmin.permission.PermissionNode;
import dev.universaladmin.scheduler.TaskScheduler;
import dev.universaladmin.settings.SettingDefinition;
import dev.universaladmin.settings.SettingKey;
import dev.universaladmin.settings.SettingTypes;
import dev.universaladmin.settings.SettingsService;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * Everything an extension can contribute, in one small module:
 *
 * <ul>
 *   <li>a <b>setting</b> ({@code example.greeting}) - editable in the settings GUI,
 *   <li>a <b>permission</b> ({@code example.greet}),
 *   <li>an <b>action</b> ({@code example:greet}) - authorized and audited by UniversalAdmin,
 *   <li>a <b>command</b> ({@code /admin greet <player>}, shortcut {@code /greet}),
 *   <li>a <b>GUI page</b> with its own main-menu button,
 *   <li>a <b>player-profile button</b> ("Greet") on every player's profile,
 *   <li>an <b>audit listener</b> that logs every action anyone performs.
 * </ul>
 *
 * Every registration goes through {@link ModuleContext}'s {@code register*}
 * methods, which undo themselves when the module is disabled.
 */
public final class ExampleModule implements Module {

    static final ModuleId ID = ModuleId.of("example", "greetings");
    static final ActionId GREET = ActionId.of("example", "greet");
    static final PermissionNode PERMISSION = PermissionNode.of("example.greet");
    static final GuiPageId HOME = GuiPageId.of("example", "home");
    static final SettingKey<String> GREETING = SettingKey.of("example", "example.greeting");

    private static final ModuleDescriptor DESCRIPTOR = ModuleDescriptor.builder(ID, "Greetings")
            .description("Example extension: greet players from a command, a GUI page or their profile.")
            .icon(new GuiIcon("cake", "Greetings"))
            .menu(HOME, PERMISSION)
            .menuOrder(500)
            .build();

    @Override
    public ModuleDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public void onEnable(ModuleContext context) {
        var platform = context.platform();

        context.registerSetting(SettingDefinition.builder(GREETING, SettingTypes.STRING, "Hello {0}, have a great day!")
                .description("The greeting sent by /admin greet - {0} is the player's name.")
                .build());
        context.registerPermission(new PermissionDefinition(PERMISSION, "Greet players", PermissionDefault.OP));

        context.registerAction(ActionDefinition.builder(new GreetAction(platform.settings(), platform.scheduler()))
                .permission(PERMISSION)
                .module(ID.key().name())
                .target(target -> Optional.of(ActionTarget.player(target.id(), target.name())))
                .auditSummary(target -> "Greeted " + target.name())
                .build());

        context.registerCommand(new GreetCommand());
        context.registerPage(new HomePage(platform));
        context.registerPlayerMenuEntry(new GreetMenuEntry(platform));

        // Every recorded audit event, from every module - e.g. to forward to
        // another system. Runs off the main thread.
        context.subscribeAudit(event -> context.logger().fine(() -> "Audit: " + event.type() + " - " + event.summary()));
    }

    /** The business logic: send the configured greeting to an online player. */
    static final class GreetAction implements Action<PlayerRef, Void> {

        private final SettingsService settings;
        private final TaskScheduler scheduler;

        GreetAction(SettingsService settings, TaskScheduler scheduler) {
            this.settings = settings;
            this.scheduler = scheduler;
        }

        @Override
        public ActionId id() {
            return GREET;
        }

        @Override
        public CompletableFuture<ActionResult<Void>> execute(ActionContext context, PlayerRef target) {
            String template = settings.get(GREETING);
            return scheduler.supplyOnMainThread(() -> {
                Player player = Bukkit.getPlayer(target.id());
                if (player == null) {
                    return ActionResult.failure(ActionResult.FailureReason.VALIDATION,
                            MessageKey.of("command.error.player-not-online"), target.name());
                }
                player.sendMessage(Component.text(template.replace("{0}", player.getName())));
                return ActionResult.success(null);
            });
        }
    }

    /** {@code /admin greet <player>} - also {@code /greet} while shortcuts are on. */
    static final class GreetCommand extends BaseAdminCommand {

        GreetCommand() {
            super("greet", PERMISSION, MessageKey.of("example.command.usage"), MessageKey.of("example.command.description"));
            shortcuts("greet");
        }

        @Override
        public void execute(CommandInvocation invocation) {
            if (invocation.argCount() < 1) {
                invocation.usage(this);
                return;
            }
            invocation.onlinePlayer(0).ifPresent(player -> invocation.run(GREET,
                    new PlayerRef(player.getUniqueId(), player.getName()), MessageKey.of("example.greeted"), player.getName()));
        }

        @Override
        public List<String> complete(CommandInvocation invocation) {
            return invocation.argCount() <= 1 ? Arguments.visiblePlayerNames(invocation.sender()) : List.of();
        }
    }

    /** The extension's own page in the {@code /admin} menu: one button per online player. */
    static final class HomePage extends AbstractGuiPage {

        private final UniversalAdmin platform;

        HomePage(UniversalAdmin platform) {
            super(HOME, platform.guiFramework(), platform.messages());
            this.platform = platform;
        }

        @Override
        protected Component title(Player viewer) {
            return text("example.gui.title");
        }

        @Override
        protected void renderContent(GuiRenderContext context) {
            int index = 0;
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (index >= GuiLayout.contentSize()) {
                    break;
                }
                PlayerRef target = new PlayerRef(online.getUniqueId(), online.getName());
                context.view().place(GuiLayout.contentSlot(index++), GuiButton.of(
                        GuiItem.playerHead(online, Component.text(online.getName()), List.of(text("example.gui.click-to-greet"))),
                        click -> greet(click, target)), context.viewer());
            }
        }

        private void greet(GuiClickContext click, PlayerRef target) {
            Player viewer = click.viewer();
            GuiActions.<PlayerRef, Void>run(platform.actionExecutor(), platform.scheduler(), platform.messages(), viewer,
                    GREET, target, ignored -> viewer.sendMessage(text("example.greeted", target.name())));
        }
    }

    /** "Greet" on every player's profile page. */
    static final class GreetMenuEntry implements PlayerMenuEntry {

        private final UniversalAdmin platform;

        GreetMenuEntry(UniversalAdmin platform) {
            this.platform = platform;
        }

        @Override
        public String id() {
            return "example:greet";
        }

        @Override
        public PermissionNode permission() {
            return PERMISSION;
        }

        @Override
        public boolean requiresOnline() {
            return true;
        }

        @Override
        public GuiItem icon(Player viewer, PlayerRef target) {
            MessageService messages = platform.messages();
            return GuiItem.of(Material.CAKE, ComponentMessages.render(messages.get(MessageKey.of("example.gui.profile-button"))));
        }

        @Override
        public void onClick(GuiClickContext context, PlayerRef target) {
            Player viewer = context.viewer();
            GuiActions.<PlayerRef, Void>run(platform.actionExecutor(), platform.scheduler(), platform.messages(), viewer,
                    GREET, target, ignored -> viewer.sendMessage(ComponentMessages.render(
                            platform.messages().get(MessageKey.of("example.greeted"), target.name()))));
        }
    }
}

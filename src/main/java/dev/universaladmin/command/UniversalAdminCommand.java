package dev.universaladmin.command;

import dev.universaladmin.action.ActionContext;
import dev.universaladmin.action.ActionId;
import dev.universaladmin.action.ActionRequest;
import dev.universaladmin.action.ActionResult;
import dev.universaladmin.action.Actor;
import dev.universaladmin.action.Source;
import dev.universaladmin.core.PluginStatus;
import dev.universaladmin.core.UniversalAdmin;
import dev.universaladmin.gui.MainMenuPage;
import dev.universaladmin.localization.ComponentMessages;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.localization.MessageService;
import dev.universaladmin.permission.PermissionNode;
import dev.universaladmin.permission.bukkit.PermissiblePermissionEvaluator;
import dev.universaladmin.settings.ConfigReloadResult;
import dev.universaladmin.settings.ReloadConfigAction;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Root {@code /admin} command (aliases {@code /ua}, {@code /uadmin} - see
 * plugin.yml). {@code /admin} opens {@link MainMenuPage} for a player (a
 * console/command-block sender can't see an inventory, so it gets the same
 * text status report as before); {@code /admin reload} is UniversalAdmin's
 * own safe config reload, never Bukkit's global {@code /reload}, and works
 * the same for either sender type. No other feature behavior; as modules
 * grow real subcommands, each one should still call into a service or
 * {@link dev.universaladmin.action.Action}, never contain logic inline -
 * see docs/architecture/gui.md (the same rule applies to commands). Both
 * branches here already follow that: the reload branch only invokes
 * {@link ReloadConfigAction} and renders its result, and opening the menu
 * only delegates to {@link MainMenuPage#open}.
 *
 * <p>This is one of the few frontends allowed to hold the whole
 * {@link UniversalAdmin} platform reference rather than a single service -
 * a root status/reload/menu command legitimately needs to read across every
 * module and core component, unlike a single-purpose GUI page or subcommand.
 *
 * <p>{@code /admin staff recover [player]} is the first real per-module
 * subcommand (see {@code ROADMAP.md}'s "Erste echte Subcommands" item) - it
 * deliberately does <b>not</b> import anything from {@code
 * dev.universaladmin.modules.moderation}: this is a core package and must
 * not depend upward on a specific built-in module (the same rule {@code
 * MainMenuPage} follows for GUI). It references the action purely by the
 * stable {@link ActionId} its namespace/name would resolve to - identical
 * in spirit to how {@code PermissionNode.core("menu.open")} below is
 * constructed inline rather than imported from anywhere - and interprets
 * the {@link ActionResult} generically, the same way {@link #reportReload}
 * never needed to import {@code ReloadConfigAction}'s result type by name
 * beyond what's already imported.
 *
 * <p>{@code /admin server broadcast|shutdown|restart|cancel} follows the
 * same rule and is console's only path to those permissions (it has no
 * GUI). Every one of those actions takes a raw {@code String} (or {@code
 * Void}) as its input, not a wrapper record from {@code
 * dev.universaladmin.modules.server.action} - deliberately, so this class
 * never needs to import one; see {@code ServerModule}'s action registrations.
 * Maintenance mode's richer enable flow (reason + custom message + optional
 * kick) needs a multi-field input the same constraint rules out here, so it
 * is GUI-only for now - see docs/user/modules/server.md.
 */
public final class UniversalAdminCommand implements CommandExecutor {

    /** Mirrors {@code ModerationActionIds.STAFF_MODE_RECOVER} - see the class javadoc for why this isn't imported instead. */
    private static final ActionId STAFF_MODE_RECOVER = ActionId.core("moderation.staffmode.recover");

    /** Mirror {@code ServerActionIds.*} - see the class javadoc for why these aren't imported instead. */
    private static final ActionId SERVER_BROADCAST_MESSAGE = ActionId.core("server.broadcast.message");
    private static final ActionId SERVER_SHUTDOWN = ActionId.core("server.shutdown");
    private static final ActionId SERVER_CANCEL_SHUTDOWN = ActionId.core("server.shutdown.cancel");
    private static final ActionId SERVER_RESTART = ActionId.core("server.restart");
    private static final ActionId SERVER_CANCEL_RESTART = ActionId.core("server.restart.cancel");

    private final UniversalAdmin platform;

    public UniversalAdminCommand(UniversalAdmin platform) {
        this.platform = platform;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            handleReload(sender);
        } else if (args.length > 1 && args[0].equalsIgnoreCase("staff") && args[1].equalsIgnoreCase("recover")) {
            handleStaffRecover(sender, args);
        } else if (args.length > 1 && args[0].equalsIgnoreCase("server")) {
            handleServer(sender, args);
        } else if (sender instanceof Player player) {
            handleOpenMenu(player);
        } else {
            handleStatus(sender);
        }
        return true;
    }

    private void handleOpenMenu(Player player) {
        MessageService messages = platform.messages();
        if (!player.hasPermission(PermissionNode.core("menu.open").value())) {
            send(player, messages.get(MessageKey.of("error.no-permission")));
            return;
        }
        platform.guiPages().get(MainMenuPage.ID).ifPresentOrElse(
                page -> page.open(player),
                () -> handleStatus(player));
    }

    private void handleStatus(CommandSender sender) {
        MessageService messages = platform.messages();
        PluginStatus status = platform.status();

        send(sender, messages.get(MessageKey.of("command.status.header"), status.version()));
        send(sender, messages.get(MessageKey.of("command.status.uptime"), formatUptime(status)));
        send(sender, messages.get(
                MessageKey.of("command.status.modules-active"), status.activeModules().size(), joinOrNone(status.activeModules())));
        send(sender, messages.get(MessageKey.of("command.status.modules-failed"), joinOrNone(status.failedModules())));
        send(sender, messages.get(MessageKey.of("command.status.database-web"), status.database(), status.web()));
    }

    private void handleReload(CommandSender sender) {
        Actor actor = actorFor(sender);
        ActionRequest<Void> request = ActionRequest.of(ReloadConfigAction.ID, actor, Source.COMMAND, null);
        platform.actionExecutor().<Void, ConfigReloadResult>execute(request)
                .thenAccept(result -> platform.scheduler().runOnMainThread(() -> reportReload(sender, result)));
    }

    private void reportReload(CommandSender sender, ActionResult<ConfigReloadResult> result) {
        MessageService messages = platform.messages();
        switch (result) {
            case ActionResult.Success<ConfigReloadResult> success -> {
                ConfigReloadResult reload = success.value();
                send(sender, messages.get(MessageKey.of("command.reload.success"), reload.applied().size()));
                if (!reload.pendingRestart().isEmpty()) {
                    send(sender, messages.get(
                            MessageKey.of("command.reload.pending-restart"), joinOrNone(reload.pendingRestart())));
                }
                if (!reload.validationErrors().isEmpty()) {
                    send(sender, messages.get(
                            MessageKey.of("command.reload.errors"), String.join("; ", reload.validationErrors())));
                }
            }
            // Authorization/lookup now run inside ActionExecutor (see docs/architecture/actions.md)
            // instead of being pre-checked here, so this maps FailureReason back
            // onto the same messages the old pre-checks used to send directly.
            case ActionResult.Failure<ConfigReloadResult> failure -> {
                MessageKey key = switch (failure.reason()) {
                    case NOT_PERMITTED -> MessageKey.of("error.no-permission");
                    case NOT_FOUND -> MessageKey.of("command.reload.unavailable");
                    default -> null;
                };
                if (key != null) {
                    send(sender, messages.get(key));
                } else {
                    send(sender, messages.get(MessageKey.of("command.reload.error"), failure.message()));
                }
            }
        }
    }

    private void handleStaffRecover(CommandSender sender, String[] args) {
        MessageService messages = platform.messages();
        UUID targetId = resolveRecoveryTarget(sender, args);
        if (targetId == null) {
            send(sender, messages.get(MessageKey.of("command.staff.recover.usage")));
            return;
        }
        Actor actor = actorFor(sender);
        platform.actionExecutor().<UUID, Object>execute(STAFF_MODE_RECOVER, new ActionContext(actor, Source.COMMAND), targetId)
                .thenAccept(result -> platform.scheduler().runOnMainThread(() -> reportStaffRecover(sender, result)));
    }

    private UUID resolveRecoveryTarget(CommandSender sender, String[] args) {
        if (args.length > 2) {
            Player online = Bukkit.getPlayer(args[2]);
            return online != null ? online.getUniqueId() : null;
        }
        return sender instanceof Player player ? player.getUniqueId() : null;
    }

    private void reportStaffRecover(CommandSender sender, ActionResult<Object> result) {
        MessageService messages = platform.messages();
        switch (result) {
            case ActionResult.Success<Object> success -> send(sender, messages.get(MessageKey.of("command.staff.recover.success")));
            case ActionResult.Failure<Object> failure -> {
                if (failure.messageKey() != null) {
                    send(sender, messages.get(failure.messageKey(), failure.messageArgs().toArray()));
                } else {
                    send(sender, messages.get(MessageKey.of("command.staff.recover.error"), failure.message()));
                }
            }
        }
    }

    private void handleServer(CommandSender sender, String[] args) {
        MessageService messages = platform.messages();
        String sub = args[1].toLowerCase(Locale.ROOT);
        Actor actor = actorFor(sender);
        switch (sub) {
            case "broadcast" -> {
                if (args.length < 3) {
                    send(sender, messages.get(MessageKey.of("command.server.broadcast.usage")));
                    return;
                }
                String message = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                runServerAction(sender, SERVER_BROADCAST_MESSAGE, actor, message);
            }
            case "shutdown" -> runServerAction(sender, SERVER_SHUTDOWN, actor, joinFrom(args, 2));
            case "restart" -> runServerAction(sender, SERVER_RESTART, actor, joinFrom(args, 2));
            case "cancel" -> handleServerCancel(sender, actor);
            default -> send(sender, messages.get(MessageKey.of("command.server.usage")));
        }
    }

    /** Tries cancelling a pending shutdown first, then a pending restart - whichever (if either) is actually active. */
    private void handleServerCancel(CommandSender sender, Actor actor) {
        CompletableFuture<ActionResult<Object>> future = platform.actionExecutor()
                .<Void, Object>execute(SERVER_CANCEL_SHUTDOWN, new ActionContext(actor, Source.COMMAND), null);
        future.thenCompose(result -> result instanceof ActionResult.Success<Object>
                        ? CompletableFuture.completedFuture(result)
                        : platform.actionExecutor().<Void, Object>execute(
                                SERVER_CANCEL_RESTART, new ActionContext(actor, Source.COMMAND), null))
                .thenAccept(result -> platform.scheduler().runOnMainThread(() -> reportServerResult(sender, result)));
    }

    private String joinFrom(String[] args, int startIndex) {
        return args.length > startIndex ? String.join(" ", Arrays.copyOfRange(args, startIndex, args.length)) : null;
    }

    private <I> void runServerAction(CommandSender sender, ActionId id, Actor actor, I input) {
        platform.actionExecutor().<I, Object>execute(id, new ActionContext(actor, Source.COMMAND), input)
                .thenAccept(result -> platform.scheduler().runOnMainThread(() -> reportServerResult(sender, result)));
    }

    private void reportServerResult(CommandSender sender, ActionResult<Object> result) {
        MessageService messages = platform.messages();
        switch (result) {
            case ActionResult.Success<Object> success -> send(sender, messages.get(MessageKey.of("command.server.success")));
            case ActionResult.Failure<Object> failure -> {
                if (failure.messageKey() != null) {
                    send(sender, messages.get(failure.messageKey(), failure.messageArgs().toArray()));
                } else {
                    send(sender, messages.get(MessageKey.of("command.server.error"), failure.message()));
                }
            }
        }
    }

    private Actor actorFor(CommandSender sender) {
        if (sender instanceof Player player) {
            return Actor.player(player.getUniqueId(), player.getName(), new PermissiblePermissionEvaluator(player));
        }
        return Actor.console();
    }

    private String joinOrNone(List<?> items) {
        return items.isEmpty()
                ? platform.messages().get(MessageKey.of("common.none"))
                : items.stream().map(Object::toString).collect(Collectors.joining(", "));
    }

    private String formatUptime(PluginStatus status) {
        long seconds = status.uptime().toSeconds();
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long remainingSeconds = seconds % 60;
        return "%dh %dm %ds".formatted(hours, minutes, remainingSeconds);
    }

    private void send(CommandSender sender, String resolvedText) {
        sender.sendMessage(ComponentMessages.render(resolvedText));
    }
}

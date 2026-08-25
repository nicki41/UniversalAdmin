package dev.universaladmin.modules.players.gui;

import dev.universaladmin.action.ActionContext;
import dev.universaladmin.action.ActionResult;
import dev.universaladmin.action.Actor;
import dev.universaladmin.action.Source;
import dev.universaladmin.localization.ComponentMessages;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.localization.MessageService;
import dev.universaladmin.permission.bukkit.PermissiblePermissionEvaluator;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/**
 * Shared glue for the ~10 Players GUI pages that call into {@link
 * dev.universaladmin.action.ActionExecutor}: building the {@link
 * ActionContext} (the same {@code actorFor(...)} shape {@code
 * UniversalAdminCommand} uses for commands) and rendering an {@link
 * ActionResult} back to the viewer as a chat message.
 */
final class PlayerGuiActions {

    private PlayerGuiActions() {
    }

    static ActionContext contextFor(Player viewer) {
        Actor actor = Actor.player(viewer.getUniqueId(), viewer.getName(), new PermissiblePermissionEvaluator(viewer));
        return new ActionContext(actor, Source.GUI);
    }

    static void notifyResult(Player viewer, MessageService messages, ActionResult<?> result) {
        Component message = switch (result) {
            case ActionResult.Success<?> success -> ComponentMessages.render(messages.get(MessageKey.of("players.gui.action.success")));
            case ActionResult.Failure<?> failure -> ComponentMessages.render(failureText(messages, failure));
        };
        viewer.sendMessage(message);
    }

    static void notifyError(Player viewer, MessageService messages) {
        viewer.sendMessage(ComponentMessages.render(messages.get(MessageKey.of("players.gui.action.error"))));
    }

    private static String failureText(MessageService messages, ActionResult.Failure<?> failure) {
        if (failure.messageKey() != null) {
            return messages.get(failure.messageKey(), failure.messageArgs().toArray());
        }
        return failure.message() != null ? failure.message() : failure.reason().name();
    }
}

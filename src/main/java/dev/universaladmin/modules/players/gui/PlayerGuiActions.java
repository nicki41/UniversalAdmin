package dev.universaladmin.modules.players.gui;

import dev.universaladmin.action.ActionContext;
import dev.universaladmin.action.ActionResult;
import dev.universaladmin.action.Actor;
import dev.universaladmin.action.Source;
import dev.universaladmin.gui.GuiActions;
import dev.universaladmin.localization.ComponentMessages;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.localization.MessageService;
import dev.universaladmin.permission.bukkit.PermissiblePermissionEvaluator;
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

    /** Tells {@code viewer} what the action did (or why it failed) - see {@link GuiActions#report}. */
    static void notifyResult(Player viewer, MessageService messages, ActionResult<?> result) {
        GuiActions.report(viewer, messages, result, MessageKey.of("players.gui.action.success"));
    }

    static void notifyError(Player viewer, MessageService messages) {
        viewer.sendMessage(ComponentMessages.render(messages.get(MessageKey.of("players.gui.action.error"))));
    }
}

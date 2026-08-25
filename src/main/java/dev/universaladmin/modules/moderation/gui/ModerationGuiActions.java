package dev.universaladmin.modules.moderation.gui;

import dev.universaladmin.action.ActionContext;
import dev.universaladmin.action.ActionId;
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
 * Shared glue for every Moderation GUI page that calls into {@link
 * dev.universaladmin.action.ActionExecutor} - building the {@link
 * ActionContext}, rendering an {@link ActionResult} back to the viewer, and
 * the {@code execute -> notify -> reopen} sequence every page needs (see
 * {@code PlayerGuiActions} in the Players module for the identical pattern).
 */
final class ModerationGuiActions {

    private ModerationGuiActions() {
    }

    static ActionContext contextFor(Player viewer) {
        Actor actor = Actor.player(viewer.getUniqueId(), viewer.getName(), new PermissiblePermissionEvaluator(viewer));
        return new ActionContext(actor, Source.GUI);
    }

    /** Runs {@code id} with {@code input}, notifies {@code viewer} of the outcome, then runs {@code onDone} back on the main thread. */
    static <I> void runAction(ModerationGuiContext ctx, Player viewer, ActionId id, I input, Runnable onDone) {
        ctx.actionExecutor().<I, Object>execute(id, contextFor(viewer), input)
                .whenComplete((result, error) -> ctx.scheduler().runOnMainThread(() -> {
                    if (!viewer.isOnline()) {
                        return;
                    }
                    if (error != null) {
                        notifyError(viewer, ctx.messages());
                    } else {
                        notifyResult(viewer, ctx.messages(), result);
                    }
                    onDone.run();
                }));
    }

    static void notifyResult(Player viewer, MessageService messages, ActionResult<?> result) {
        Component message = switch (result) {
            case ActionResult.Success<?> success -> ComponentMessages.render(messages.get(MessageKey.of("moderation.gui.action.success")));
            case ActionResult.Failure<?> failure -> ComponentMessages.render(failureText(messages, failure));
        };
        viewer.sendMessage(message);
    }

    static void notifyError(Player viewer, MessageService messages) {
        viewer.sendMessage(ComponentMessages.render(messages.get(MessageKey.of("moderation.gui.action.error"))));
    }

    private static String failureText(MessageService messages, ActionResult.Failure<?> failure) {
        if (failure.messageKey() != null) {
            return messages.get(failure.messageKey(), failure.messageArgs().toArray());
        }
        return failure.message() != null ? failure.message() : failure.reason().name();
    }
}

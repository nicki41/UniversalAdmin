package dev.universaladmin.modules.performance.gui;

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

/** Shared glue for every Performance GUI page that calls into {@link dev.universaladmin.action.ActionExecutor} - identical shape to {@code ServerGuiActions}. */
final class PerformanceGuiActions {

    private PerformanceGuiActions() {
    }

    static ActionContext contextFor(Player viewer) {
        Actor actor = Actor.player(viewer.getUniqueId(), viewer.getName(), new PermissiblePermissionEvaluator(viewer));
        return new ActionContext(actor, Source.GUI);
    }

    static <I> void runAction(PerformanceGuiContext ctx, Player viewer, ActionId id, I input, Runnable onDone) {
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
            case ActionResult.Success<?> success -> ComponentMessages.render(messages.get(MessageKey.of("performance.gui.action.success")));
            case ActionResult.Failure<?> failure -> ComponentMessages.render(failureText(messages, failure));
        };
        viewer.sendMessage(message);
    }

    static void notifyError(Player viewer, MessageService messages) {
        viewer.sendMessage(ComponentMessages.render(messages.get(MessageKey.of("performance.gui.action.error"))));
    }

    private static String failureText(MessageService messages, ActionResult.Failure<?> failure) {
        if (failure.messageKey() != null) {
            return messages.get(failure.messageKey(), failure.messageArgs().toArray());
        }
        return failure.message() != null ? failure.message() : failure.reason().name();
    }
}

package dev.universaladmin.gui;

import dev.universaladmin.action.ActionContext;
import dev.universaladmin.action.ActionExecutor;
import dev.universaladmin.action.ActionId;
import dev.universaladmin.action.ActionResult;
import dev.universaladmin.action.Actor;
import dev.universaladmin.action.Source;
import dev.universaladmin.core.PlayerNames;
import dev.universaladmin.localization.ComponentMessages;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.localization.MessageService;
import dev.universaladmin.localization.ResultMessages;
import dev.universaladmin.permission.bukkit.PermissiblePermissionEvaluator;
import dev.universaladmin.scheduler.TaskScheduler;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.entity.Player;

/**
 * Runs an {@link dev.universaladmin.action.Action} from a GUI click and
 * reports a failure to the viewer - the one helper every page needs, so a
 * click handler stays a one-liner and never touches threading itself.
 *
 * <pre>{@code
 * GuiActions.run(executor, scheduler, messages, viewer, MyActionIds.DO_IT, input,
 *         result -> this.open(viewer));
 * }</pre>
 */
public final class GuiActions {

    private static final Logger LOGGER = Logger.getLogger(GuiActions.class.getName());

    private GuiActions() {
    }

    /** The {@link ActionContext} for an action a player triggered from a GUI. */
    public static ActionContext contextFor(Player viewer) {
        Actor actor = Actor.player(viewer.getUniqueId(), PlayerNames.real(viewer), new PermissiblePermissionEvaluator(viewer));
        return new ActionContext(actor, Source.GUI);
    }

    /**
     * Executes {@code id} as {@code viewer}. On success {@code onSuccess}
     * runs on the main thread (only if the viewer is still online); on
     * failure the viewer gets the failure's message (or a generic error) and
     * {@code onSuccess} is skipped.
     */
    public static <I, R> void run(
            ActionExecutor executor, TaskScheduler scheduler, MessageService messages,
            Player viewer, ActionId id, I input, Consumer<R> onSuccess) {
        executor.<I, R>execute(id, contextFor(viewer), input).whenComplete((result, error) -> scheduler.runOnMainThread(() -> {
            if (!viewer.isOnline()) {
                return;
            }
            if (error != null) {
                LOGGER.log(Level.SEVERE, "GUI action " + id + " failed unexpectedly", error);
                viewer.sendMessage(ComponentMessages.render(messages.get(MessageKey.of("gui.error"))));
                return;
            }
            switch (result) {
                case ActionResult.Success<R> success -> onSuccess.accept(success.value());
                case ActionResult.Failure<R> failure -> report(viewer, messages, failure, MessageKey.of("command.done"));
            }
        }));
    }

    /**
     * Executes {@code id} as {@code viewer} and tells them the outcome in
     * chat - what the action reports it did (or {@code successFallback}), or
     * why it failed. {@code afterwards} runs on the main thread either way
     * (only if the viewer is still online), e.g. to re-render the page.
     */
    public static <I> void runAndReport(
            ActionExecutor executor, TaskScheduler scheduler, MessageService messages,
            Player viewer, ActionId id, I input, MessageKey successFallback, Runnable afterwards) {
        executor.<I, Object>execute(id, contextFor(viewer), input).whenComplete((result, error) -> scheduler.runOnMainThread(() -> {
            if (!viewer.isOnline()) {
                return;
            }
            if (error != null) {
                LOGGER.log(Level.SEVERE, "GUI action " + id + " failed unexpectedly", error);
                viewer.sendMessage(ComponentMessages.render(messages.get(MessageKey.of("gui.error"))));
            } else {
                report(viewer, messages, result, successFallback);
            }
            afterwards.run();
        }));
    }

    /**
     * Sends {@code result}'s text to {@code viewer}, with the same chat prefix
     * commands use. With {@code successFallback == null} a success the action
     * did not describe sends nothing - for actions whose effect is already
     * visible (e.g. a broadcast everyone, the viewer included, receives).
     */
    public static void report(Player viewer, MessageService messages, ActionResult<?> result, MessageKey successFallback) {
        if (successFallback == null && result instanceof ActionResult.Success<?> success && success.messageKey() == null) {
            return;
        }
        viewer.sendMessage(ComponentMessages.render(
                messages.get(MessageKey.of("command.prefix")) + ResultMessages.of(messages, result, successFallback)));
    }

    /** The localized text for a failure - its own message key if it has one, otherwise a generic one per reason. */
    public static String failureText(MessageService messages, ActionResult.Failure<?> failure) {
        return ResultMessages.failure(messages, failure);
    }
}

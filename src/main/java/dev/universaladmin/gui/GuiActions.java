package dev.universaladmin.gui;

import dev.universaladmin.action.ActionContext;
import dev.universaladmin.action.ActionExecutor;
import dev.universaladmin.action.ActionId;
import dev.universaladmin.action.ActionResult;
import dev.universaladmin.action.Actor;
import dev.universaladmin.action.Source;
import dev.universaladmin.localization.ComponentMessages;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.localization.MessageService;
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
        Actor actor = Actor.player(viewer.getUniqueId(), viewer.getName(), new PermissiblePermissionEvaluator(viewer));
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
                case ActionResult.Failure<R> failure -> viewer.sendMessage(ComponentMessages.render(failureText(messages, failure)));
            }
        }));
    }

    /** The localized text for a failure - its own message key if it has one, otherwise a generic one per reason. */
    public static String failureText(MessageService messages, ActionResult.Failure<?> failure) {
        if (failure.messageKey() != null) {
            return messages.get(failure.messageKey(), failure.messageArgs().toArray());
        }
        return switch (failure.reason()) {
            case NOT_PERMITTED -> messages.get(MessageKey.of("error.no-permission"));
            case FEATURE_DISABLED -> messages.get(MessageKey.of("command.error.disabled"));
            case VALIDATION, CONFLICT, NOT_FOUND -> messages.get(MessageKey.of("command.error.invalid"),
                    failure.message() == null ? failure.reason().name() : failure.message());
            case INTERNAL_ERROR -> messages.get(MessageKey.of("command.error.internal"));
        };
    }
}

package dev.universaladmin.action;

import dev.universaladmin.localization.MessageKey;
import java.util.List;
import java.util.Map;

/**
 * Outcome of an {@link Action}. A sealed type instead of exceptions or
 * {@code null} so every frontend (GUI, command, web API) is forced to handle
 * failure explicitly and can render {@link Failure#reason()} to the user
 * without knowing anything about the action's internals.
 *
 * <p>{@code messageKey}/{@code messageArgs} let a failure be rendered through
 * {@link dev.universaladmin.localization.MessageService} like any other
 * user-facing text (see {@link dev.universaladmin.modules.players.action.GetPlayerProfileAction}
 * for an example); {@code message} remains available for a plain, non-localized
 * detail (logs, debugging, or a frontend that has no better key to show).
 * {@code metadata} is a small, action-defined bag for anything else worth
 * carrying alongside the result (e.g. an affected-count a GUI wants to
 * display) without inventing a new result type per action.
 */
public sealed interface ActionResult<R> {

    record Success<R>(R value, Map<String, Object> metadata) implements ActionResult<R> {
        public Success {
            metadata = Map.copyOf(metadata);
        }
    }

    record Failure<R>(FailureReason reason, String message, MessageKey messageKey, List<Object> messageArgs, Map<String, Object> metadata)
            implements ActionResult<R> {
        public Failure {
            messageArgs = List.copyOf(messageArgs);
            metadata = Map.copyOf(metadata);
        }
    }

    static <R> ActionResult<R> success(R value) {
        return new Success<>(value, Map.of());
    }

    static <R> ActionResult<R> success(R value, Map<String, Object> metadata) {
        return new Success<>(value, metadata);
    }

    static <R> ActionResult<R> failure(FailureReason reason, String message) {
        return new Failure<>(reason, message, null, List.of(), Map.of());
    }

    static <R> ActionResult<R> failure(FailureReason reason, MessageKey messageKey, Object... args) {
        return new Failure<>(reason, null, messageKey, List.of(args), Map.of());
    }

    static <R> ActionResult<R> failure(
            FailureReason reason, String message, MessageKey messageKey, Map<String, Object> metadata, Object... args) {
        return new Failure<>(reason, message, messageKey, List.of(args), metadata);
    }

    /** Broad categories a frontend can use to decide how to present a failure. */
    enum FailureReason {
        VALIDATION,
        NOT_FOUND,
        NOT_PERMITTED,
        CONFLICT,
        FEATURE_DISABLED,
        INTERNAL_ERROR
    }
}

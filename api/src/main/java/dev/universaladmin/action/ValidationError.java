package dev.universaladmin.action;

import dev.universaladmin.localization.MessageKey;
import java.util.List;

/**
 * A structured, expected validation failure from an {@link ActionValidator} -
 * never an exception, since an invalid input/target is a normal user error,
 * not a programming error. {@link ActionExecutor} turns this into an
 * {@link ActionResult.Failure} before the wrapped {@link Action} ever runs.
 */
public record ValidationError(ActionResult.FailureReason reason, String message, MessageKey messageKey, List<Object> messageArgs) {

    public ValidationError {
        messageArgs = List.copyOf(messageArgs);
    }

    /** A validation failure with only a debug-oriented message, no localized key. */
    public static ValidationError of(ActionResult.FailureReason reason, String message) {
        return new ValidationError(reason, message, null, List.of());
    }

    /** A validation failure a frontend can render via {@link dev.universaladmin.localization.MessageService}. */
    public static ValidationError of(ActionResult.FailureReason reason, MessageKey messageKey, Object... args) {
        return new ValidationError(reason, null, messageKey, List.of(args));
    }
}

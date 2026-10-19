package dev.universaladmin.localization;

import dev.universaladmin.action.ActionResult;
import java.util.List;

/**
 * The text a user sees for an {@link ActionResult} - the same whether the
 * action ran from a command, a GUI click or an extension. A success shows
 * what the action itself reported ({@link ActionResult#success(Object,
 * MessageKey, Object...)}), falling back to the caller's default; a failure
 * shows its own message or a generic one per reason.
 */
public final class ResultMessages {

    private static final MessageKey NO_PERMISSION = MessageKey.of("error.no-permission");
    private static final MessageKey DISABLED = MessageKey.of("command.error.disabled");
    private static final MessageKey NOT_FOUND = MessageKey.of("command.error.not-found");
    private static final MessageKey INVALID = MessageKey.of("command.error.invalid");
    private static final MessageKey INTERNAL_ERROR = MessageKey.of("command.error.internal");

    private ResultMessages() {
    }

    /**
     * The success's own description, or {@code fallback} if the action gave
     * none. An argument that is itself a {@link MessageKey} is resolved first,
     * so an action can pass e.g. {@code MessageKey.of("worlds.difficulty.hard")}
     * and the user reads "Hard" in their language.
     */
    public static String success(MessageService messages, ActionResult.Success<?> success, MessageKey fallback, Object... fallbackArgs) {
        if (success.messageKey() != null) {
            return messages.get(success.messageKey(), resolve(messages, success.messageArgs()));
        }
        return messages.get(fallback, fallbackArgs);
    }

    /** The failure's own message (arguments resolved like {@link #success}), or a generic text for its reason. */
    public static String failure(MessageService messages, ActionResult.Failure<?> failure) {
        if (failure.messageKey() != null) {
            return messages.get(failure.messageKey(), resolve(messages, failure.messageArgs()));
        }
        String detail = failure.message() == null ? failure.reason().name() : failure.message();
        return switch (failure.reason()) {
            case NOT_PERMITTED -> messages.get(NO_PERMISSION);
            case FEATURE_DISABLED -> messages.get(DISABLED);
            case NOT_FOUND -> messages.get(NOT_FOUND, detail);
            case VALIDATION, CONFLICT -> messages.get(INVALID, detail);
            case INTERNAL_ERROR -> messages.get(INTERNAL_ERROR);
        };
    }

    /** Lang text is trusted markup, so a resolved key keeps its colours; every other argument stays escaped. */
    private static Object[] resolve(MessageService messages, List<Object> args) {
        Object[] resolved = args.toArray();
        for (int i = 0; i < resolved.length; i++) {
            if (resolved[i] instanceof MessageKey key) {
                resolved[i] = MessageService.markup(messages.get(key));
            }
        }
        return resolved;
    }

    /** {@link #success} or {@link #failure}, whichever {@code result} is. */
    public static String of(MessageService messages, ActionResult<?> result, MessageKey successFallback) {
        return switch (result) {
            case ActionResult.Success<?> success -> success(messages, success, successFallback);
            case ActionResult.Failure<?> failure -> failure(messages, failure);
        };
    }
}

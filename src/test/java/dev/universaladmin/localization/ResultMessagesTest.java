package dev.universaladmin.localization;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.universaladmin.action.ActionResult;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ResultMessagesTest {

    /** Joins key and arguments so the test can see what was resolved with what. */
    private static final MessageService MESSAGES = (key, args) -> {
        StringBuilder text = new StringBuilder(key.value());
        for (Object arg : args) {
            text.append('|').append(arg instanceof MessageService.Markup markup ? "markup:" + markup.value() : arg);
        }
        return text.toString();
    };

    @Test
    void aSuccessShowsWhatTheActionReported() {
        ActionResult<Void> result = ActionResult.success(null, MessageKey.of("worlds.result.spawn-set"), "world", "120 64 -30");

        assertEquals("worlds.result.spawn-set|world|120 64 -30", ResultMessages.of(MESSAGES, result, MessageKey.of("command.done")));
    }

    @Test
    void aSuccessWithoutDescriptionFallsBack() {
        assertEquals("command.done", ResultMessages.of(MESSAGES, ActionResult.success(null), MessageKey.of("command.done")));
        assertEquals("command.done", ResultMessages.of(MESSAGES, ActionResult.success(null, Map.of("n", 1)), MessageKey.of("command.done")));
    }

    @Test
    void aMessageKeyArgumentIsResolvedAsTrustedMarkup() {
        ActionResult<Void> result = ActionResult.success(null, MessageKey.of("worlds.result.difficulty-set"),
                "world", MessageKey.of("worlds.difficulty.hard"));

        assertEquals("worlds.result.difficulty-set|world|markup:worlds.difficulty.hard",
                ResultMessages.of(MESSAGES, result, MessageKey.of("command.done")));
    }

    @Test
    void aFailureShowsItsOwnMessageOrAGenericOnePerReason() {
        assertEquals("moderation.action.not-banned",
                ResultMessages.of(MESSAGES, ActionResult.failure(ActionResult.FailureReason.NOT_FOUND,
                        MessageKey.of("moderation.action.not-banned")), MessageKey.of("command.done")));
        assertEquals("error.no-permission",
                ResultMessages.of(MESSAGES, ActionResult.failure(ActionResult.FailureReason.NOT_PERMITTED, "denied"),
                        MessageKey.of("command.done")));
        assertEquals("command.error.not-found|Steve",
                ResultMessages.of(MESSAGES, ActionResult.failure(ActionResult.FailureReason.NOT_FOUND, "Steve"),
                        MessageKey.of("command.done")));
    }
}

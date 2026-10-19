package dev.universaladmin.modules.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ChatServiceTest {

    private static final UUID PLAYER = UUID.randomUUID();

    private final MutableClock clock = new MutableClock();
    private final ChatService chat = new ChatService(clock);

    @Test
    void allowsEverythingByDefault() {
        assertInstanceOf(ChatService.Decision.Allowed.class, chat.check(PLAYER, false));
        assertInstanceOf(ChatService.Decision.Allowed.class, chat.check(PLAYER, false));
    }

    @Test
    void aLockedChatBlocksEveryoneExceptBypass() {
        assertTrue(chat.setLocked(true));
        assertFalse(chat.setLocked(true), "locking twice is not a change");

        assertInstanceOf(ChatService.Decision.Locked.class, chat.check(PLAYER, false));
        assertInstanceOf(ChatService.Decision.Allowed.class, chat.check(PLAYER, true));
    }

    @Test
    void slowModeAllowsOneMessagePerInterval() {
        chat.setSlowMode(Duration.ofSeconds(10));

        assertInstanceOf(ChatService.Decision.Allowed.class, chat.check(PLAYER, false));
        clock.advance(Duration.ofSeconds(4));
        ChatService.Decision tooSoon = chat.check(PLAYER, false);
        assertEquals(Duration.ofSeconds(6), assertInstanceOf(ChatService.Decision.SlowMode.class, tooSoon).retryAfter());

        clock.advance(Duration.ofSeconds(6));
        assertInstanceOf(ChatService.Decision.Allowed.class, chat.check(PLAYER, false));
    }

    @Test
    void slowModeIsPerPlayerAndIgnoresBypass() {
        chat.setSlowMode(Duration.ofSeconds(10));
        chat.check(PLAYER, false);

        assertInstanceOf(ChatService.Decision.Allowed.class, chat.check(UUID.randomUUID(), false));
        assertInstanceOf(ChatService.Decision.Allowed.class, chat.check(PLAYER, true));
    }

    @Test
    void turningSlowModeOffLiftsItImmediately() {
        chat.setSlowMode(Duration.ofSeconds(10));
        chat.check(PLAYER, false);
        chat.setSlowMode(Duration.ZERO);

        assertInstanceOf(ChatService.Decision.Allowed.class, chat.check(PLAYER, false));
    }

    @Test
    void staffChatToggleFlipsAndForgetResetsIt() {
        assertTrue(chat.toggleStaffChat(PLAYER));
        assertTrue(chat.staffChatToggled(PLAYER));
        assertFalse(chat.toggleStaffChat(PLAYER));

        chat.toggleStaffChat(PLAYER);
        chat.forget(PLAYER);
        assertFalse(chat.staffChatToggled(PLAYER));
    }

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-10-01T12:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}

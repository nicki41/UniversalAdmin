package dev.universaladmin.modules.players;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PlayerSessionTrackerTest {

    private final PlayerSessionTracker tracker = new PlayerSessionTracker();

    @Test
    void aPlayerWhoNeverLoggedInHasNoSessionDuration() {
        assertTrue(tracker.sessionDuration(UUID.randomUUID()).isEmpty());
    }

    @Test
    void sessionDurationGrowsFromLogin() throws InterruptedException {
        UUID playerId = UUID.randomUUID();
        tracker.recordLogin(playerId);
        Thread.sleep(5);

        Optional<Duration> duration = tracker.sessionDuration(playerId);

        assertTrue(duration.isPresent());
        assertFalse(duration.get().isNegative());
    }

    @Test
    void logoutClearsTheSession() {
        UUID playerId = UUID.randomUUID();
        tracker.recordLogin(playerId);

        tracker.recordLogout(playerId);

        assertTrue(tracker.sessionDuration(playerId).isEmpty());
    }
}

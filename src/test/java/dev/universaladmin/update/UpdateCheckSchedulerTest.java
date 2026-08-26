package dev.universaladmin.update;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.universaladmin.notification.NotificationService;
import dev.universaladmin.notification.Notification;
import dev.universaladmin.permission.PermissionNode;
import dev.universaladmin.settings.CoreSettings;
import dev.universaladmin.settings.SettingRegistry;
import dev.universaladmin.settings.SettingsService;
import dev.universaladmin.settings.YamlSettingsService;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.util.random.RandomGenerator;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/** Timing behaviour of the update-check timer - jitter maths tested directly, the same "no wall-clock waiting" shape as {@code TelemetrySchedulerTest}. */
class UpdateCheckSchedulerTest {

    private static final Logger LOGGER = Logger.getLogger("update-check-test");
    private static final RandomGenerator RANDOM = RandomGenerator.getDefault();
    private static final NotificationService NO_OP_NOTIFICATIONS = new NotificationService() {
        @Override
        public void notifyPlayer(UUID playerId, Notification notification) {
        }

        @Override
        public void notifyStaff(PermissionNode requiredPermission, Notification notification) {
        }

        @Override
        public void broadcast(net.kyori.adventure.text.Component message) {
        }

        @Override
        public void broadcastTitle(net.kyori.adventure.text.Component title, net.kyori.adventure.text.Component subtitle,
                Duration fadeIn, Duration stay, Duration fadeOut) {
        }

        @Override
        public void broadcastActionBar(net.kyori.adventure.text.Component message) {
        }
    };

    private static UpdateCheckService serviceThatCounts(AtomicInteger counter) {
        SettingRegistry registry = new SettingRegistry();
        CoreSettings.registerAll(registry);
        SettingsService settings = new YamlSettingsService(registry, YamlConfiguration::new, LOGGER);
        GitHubReleaseClient counting = () -> {
            counter.incrementAndGet();
            return new GitHubRelease("v0.1.0-alpha", "https://example.com", false, List.of());
        };
        return new UpdateCheckService(
                settings, new InlineTaskScheduler(), counting, NO_OP_NOTIFICATIONS,
                (key, args) -> key.value(), PermissionNode.core("update.notify"), "0.1.0-alpha", LOGGER);
    }

    @Test
    void jitterStaysBetweenTheIntervalAndOneAndAHalfTimesIt() {
        Duration base = Duration.ofHours(6);

        for (int i = 0; i < 10_000; i++) {
            Duration delay = UpdateCheckScheduler.jittered(base, RANDOM);

            assertTrue(delay.compareTo(base) >= 0, "never earlier than the configured interval: " + delay);
            assertTrue(delay.compareTo(base.multipliedBy(3).dividedBy(2)) <= 0, "never more than +50%: " + delay);
        }
    }

    @Test
    void jitterActuallyVaries() {
        Set<Long> distinct = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            distinct.add(UpdateCheckScheduler.jittered(Duration.ofHours(6), RANDOM).toMillis());
        }

        assertTrue(distinct.size() > 100, "expected a wide spread of delays, got " + distinct.size());
    }

    @Test
    void closeStopsTheTimerAndIsSafeToCallTwice() {
        AtomicInteger checks = new AtomicInteger();
        UpdateCheckScheduler scheduler = new UpdateCheckScheduler(serviceThatCounts(checks), Duration.ofHours(6), RANDOM, LOGGER);
        scheduler.start();

        scheduler.close();
        scheduler.close();

        assertTrue(scheduler.isClosed());
        assertDoesNotThrow(scheduler::start);
        assertEquals(0, checks.get(), "the first check is minutes away and must not fire after close()");
    }

    @Test
    void doesNotCheckDuringTheInitialDelay() throws InterruptedException {
        AtomicInteger checks = new AtomicInteger();
        UpdateCheckScheduler scheduler = new UpdateCheckScheduler(serviceThatCounts(checks), Duration.ofHours(6), RANDOM, LOGGER);

        try {
            scheduler.start();
            assertFalse(scheduler.isClosed());
            TimeUnit.MILLISECONDS.sleep(50);

            assertEquals(0, checks.get());
        } finally {
            scheduler.close();
        }
    }
}

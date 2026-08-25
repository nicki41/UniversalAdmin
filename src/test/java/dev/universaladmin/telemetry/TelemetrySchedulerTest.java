package dev.universaladmin.telemetry;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.universaladmin.settings.CoreSettings;
import dev.universaladmin.settings.SettingRegistry;
import dev.universaladmin.settings.SettingsService;
import dev.universaladmin.settings.YamlSettingsService;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.util.random.RandomGenerator;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/**
 * Timing behaviour and lifecycle of the heartbeat timer. The jitter maths is
 * tested directly rather than by waiting for wall-clock time to pass, so
 * nothing here depends on how fast the machine running the tests is.
 */
class TelemetrySchedulerTest {

    private static final Logger LOGGER = Logger.getLogger("telemetry-test");
    private static final RandomGenerator RANDOM = RandomGenerator.getDefault();

    private static TelemetryService serviceThatCounts(AtomicInteger counter) {
        SettingRegistry registry = new SettingRegistry();
        CoreSettings.registerAll(registry);
        SettingsService settings = new YamlSettingsService(registry, YamlConfiguration::new, LOGGER);
        TelemetryClient counting = new TelemetryClient() {
            @Override
            public void send(TelemetryPayload payload) {
                counter.incrementAndGet();
            }

            @Override
            public void close() {
            }
        };
        return new TelemetryService(
                settings,
                new InlineTaskScheduler(),
                counting,
                InstallationIdentity.generate(),
                new TelemetryEnvironment("0.1.0-alpha", "1.21.4", 25),
                () -> new PlayerCounts(0, 20),
                LOGGER);
    }

    @Test
    void jitterStaysBetweenTheIntervalAndOneAndAHalfTimesIt() {
        Duration base = Duration.ofMinutes(30);

        for (int i = 0; i < 10_000; i++) {
            Duration delay = TelemetryScheduler.jittered(base, RANDOM);

            assertTrue(delay.compareTo(base) >= 0, "never earlier than the configured interval: " + delay);
            assertTrue(delay.compareTo(base.multipliedBy(3).dividedBy(2)) <= 0, "never more than +50%: " + delay);
        }
    }

    @Test
    void jitterActuallyVariesSoServersDoNotSendInLockstep() {
        Set<Long> distinct = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            distinct.add(TelemetryScheduler.jittered(Duration.ofMinutes(30), RANDOM).toMillis());
        }

        assertTrue(distinct.size() > 100, "expected a wide spread of delays, got " + distinct.size());
    }

    @Test
    void rejectsAnIntervalBelowTheFloor() {
        AtomicInteger sent = new AtomicInteger();

        assertThrows(IllegalArgumentException.class, () -> new TelemetryScheduler(
                serviceThatCounts(sent), Duration.ofSeconds(30), RANDOM, LOGGER));
    }

    @Test
    void closeStopsTheTimerAndIsSafeToCallTwice() {
        AtomicInteger sent = new AtomicInteger();
        TelemetryScheduler scheduler = new TelemetryScheduler(
                serviceThatCounts(sent), Duration.ofMinutes(30), RANDOM, LOGGER);
        scheduler.start();

        scheduler.close();
        scheduler.close();

        assertTrue(scheduler.isClosed());
        // start() after close() must not resurrect the timer.
        assertDoesNotThrow(scheduler::start);
        assertEquals(0, sent.get(), "the first heartbeat is minutes away and must not fire after close()");
    }

    @Test
    void doesNotSendAnythingDuringTheInitialDelay() throws InterruptedException {
        AtomicInteger sent = new AtomicInteger();
        TelemetryScheduler scheduler = new TelemetryScheduler(
                serviceThatCounts(sent), Duration.ofMinutes(5), RANDOM, LOGGER);

        try {
            scheduler.start();
            assertFalse(scheduler.isClosed());
            TimeUnit.MILLISECONDS.sleep(50);

            // The first heartbeat is INITIAL_DELAY (5 minutes) plus jitter away,
            // so a freshly started server is never hit by one during startup.
            assertEquals(0, sent.get());
        } finally {
            scheduler.close();
        }
    }
}

package dev.universaladmin.telemetry;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.random.RandomGenerator;

/**
 * Decides <i>when</i> {@link TelemetryService#sendHeartbeat()} runs.
 *
 * <h2>Timing</h2>
 *
 * <ul>
 *   <li>The first heartbeat waits {@link #INITIAL_DELAY} plus a random part,
 *       so it never lands in the middle of a server's startup burst.
 *   <li>Every following heartbeat waits the configured interval (default 30
 *       minutes, never below {@link #MINIMUM_INTERVAL}) plus a random extra
 *       of up to half that interval - so a default install sends roughly
 *       every 30-45 minutes, and thousands of servers that restarted after
 *       the same outage don't line up into a synchronised wave.
 *   <li>Each delay is drawn fresh; this re-schedules a one-shot task instead
 *       of running at a fixed rate, so jitter doesn't accumulate into a
 *       drifting-but-still-synchronised schedule.
 * </ul>
 *
 * <p>Its own single daemon thread, not the Paper scheduler: this must keep
 * ticking without a Bukkit task, must never delay shutdown, and does nothing
 * but hand work to {@link TelemetryService} (which immediately hops to the
 * right thread). {@link #close()} stops it, and a closed scheduler never
 * schedules again.
 */
public final class TelemetryScheduler implements AutoCloseable {

    /** Long enough that a heartbeat never competes with world/plugin loading. */
    public static final Duration INITIAL_DELAY = Duration.ofMinutes(5);

    /** Floor for the configured interval; also mirrored by the setting's own validator. */
    public static final Duration MINIMUM_INTERVAL = Duration.ofMinutes(5);

    private final TelemetryService service;
    private final Duration interval;
    private final RandomGenerator random;
    private final Logger logger;
    private final ScheduledExecutorService timer;
    private final AtomicBoolean closed = new AtomicBoolean();

    public TelemetryScheduler(TelemetryService service, Duration interval, RandomGenerator random, Logger logger) {
        this.service = Objects.requireNonNull(service, "service");
        this.interval = Objects.requireNonNull(interval, "interval");
        this.random = Objects.requireNonNull(random, "random");
        this.logger = Objects.requireNonNull(logger, "logger");
        if (interval.compareTo(MINIMUM_INTERVAL) < 0) {
            throw new IllegalArgumentException("Telemetry interval must be at least " + MINIMUM_INTERVAL);
        }
        this.timer = Executors.newSingleThreadScheduledExecutor(daemonThreadFactory());
    }

    /** Schedules the first heartbeat. Calling it on a closed scheduler does nothing. */
    public void start() {
        schedule(jittered(INITIAL_DELAY, random));
    }

    private void schedule(Duration delay) {
        if (closed.get()) {
            return;
        }
        try {
            timer.schedule(this::tick, delay.toMillis(), TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.RejectedExecutionException e) {
            // Raced with close(); nothing to do and nothing worth logging above FINE.
            logger.log(Level.FINE, "Telemetry scheduler already shut down.", e);
        }
    }

    private void tick() {
        try {
            service.sendHeartbeat();
        } catch (RuntimeException e) {
            // sendHeartbeat swallows its own failures; this is belt and braces
            // so a bug there can never kill the timer thread silently.
            logger.log(Level.FINE, "Telemetry heartbeat could not be started.", e);
        } finally {
            schedule(jittered(interval, random));
        }
    }

    /**
     * {@code base} plus a random extra of up to half of {@code base}. Pure and
     * package-visible so the jitter bounds are unit-testable without waiting
     * for real time to pass.
     */
    static Duration jittered(Duration base, RandomGenerator random) {
        long baseMillis = base.toMillis();
        long extra = baseMillis <= 1 ? 0 : random.nextLong(baseMillis / 2 + 1);
        return Duration.ofMillis(baseMillis + extra);
    }

    /** Whether {@link #close()} has run - i.e. no further heartbeat will be scheduled. */
    public boolean isClosed() {
        return closed.get();
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            timer.shutdownNow();
        }
    }

    private static ThreadFactory daemonThreadFactory() {
        return runnable -> {
            Thread thread = new Thread(runnable, "UniversalAdmin-telemetry");
            thread.setDaemon(true);
            return thread;
        };
    }
}

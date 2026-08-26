package dev.universaladmin.update;

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
 * Decides <i>when</i> {@link UpdateCheckService#checkNow()} runs - the exact
 * same jittered-single-shot-reschedule shape as {@code telemetry.TelemetryScheduler}
 * (see that class's javadoc for the full reasoning): the first check is
 * delayed so it never lands mid-startup, every following one waits the
 * configured interval plus up to half again in random jitter so many
 * servers that started at the same time don't all poll GitHub in lockstep,
 * and delays are drawn fresh each time rather than run at a fixed rate so
 * jitter doesn't collapse into a synchronised-but-drifting schedule.
 *
 * <p>Its own single daemon thread, not the Paper scheduler, for the same
 * reason telemetry's does: this must keep ticking without a Bukkit task and
 * must never delay shutdown.
 */
public final class UpdateCheckScheduler implements AutoCloseable {

    /** Long enough that a check never competes with world/plugin loading. */
    public static final Duration INITIAL_DELAY = Duration.ofMinutes(2);

    private final UpdateCheckService service;
    private final Duration interval;
    private final RandomGenerator random;
    private final Logger logger;
    private final ScheduledExecutorService timer;
    private final AtomicBoolean closed = new AtomicBoolean();

    public UpdateCheckScheduler(UpdateCheckService service, Duration interval, RandomGenerator random, Logger logger) {
        this.service = Objects.requireNonNull(service, "service");
        this.interval = Objects.requireNonNull(interval, "interval");
        this.random = Objects.requireNonNull(random, "random");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.timer = Executors.newSingleThreadScheduledExecutor(daemonThreadFactory());
    }

    /** Schedules the first check. Calling it on a closed scheduler does nothing. */
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
            logger.log(Level.FINE, "Update check scheduler already shut down.", e);
        }
    }

    private void tick() {
        try {
            service.checkNow();
        } catch (RuntimeException e) {
            logger.log(Level.FINE, "Update check could not be started.", e);
        } finally {
            schedule(jittered(interval, random));
        }
    }

    /** {@code base} plus a random extra of up to half of {@code base} - package-visible so the jitter bounds are unit-testable. */
    static Duration jittered(Duration base, RandomGenerator random) {
        long baseMillis = base.toMillis();
        long extra = baseMillis <= 1 ? 0 : random.nextLong(baseMillis / 2 + 1);
        return Duration.ofMillis(baseMillis + extra);
    }

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
            Thread thread = new Thread(runnable, "UniversalAdmin-update-check");
            thread.setDaemon(true);
            return thread;
        };
    }
}

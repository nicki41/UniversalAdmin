package dev.universaladmin.telemetry;

import dev.universaladmin.scheduler.TaskScheduler;
import dev.universaladmin.settings.CoreSettings;
import dev.universaladmin.settings.SettingsService;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Builds and delivers one heartbeat. The <i>what</i> of telemetry;
 * {@link TelemetryScheduler} owns the <i>when</i>.
 *
 * <h2>Guarantees this class exists to keep</h2>
 *
 * <ul>
 *   <li><b>Off means off.</b> {@code telemetry.enabled} is read from
 *       {@link SettingsService} on <i>every</i> heartbeat, not cached at
 *       startup, so {@code /admin reload} with {@code enabled: false} stops
 *       traffic immediately. When it is off, no payload is built and the
 *       client is not touched at all - there is no second "essential"
 *       channel and no fallback request.
 *   <li><b>Never on the main thread.</b> Player counts are read on the main
 *       thread (they are main-thread state), then the request itself runs on
 *       a background thread via {@link TaskScheduler} - see
 *       docs/architecture/threading.md.
 *   <li><b>A backend outage is a non-event.</b> Every failure is swallowed
 *       here. Nothing retries, nothing queues, nothing propagates into
 *       plugin behaviour, and the log cannot fill up: the first failure of a
 *       run is a single warning, everything after it is {@code FINE}.
 * </ul>
 */
public final class TelemetryService {

    private final SettingsService settings;
    private final TaskScheduler scheduler;
    private final TelemetryClient client;
    private final InstallationIdentity identity;
    private final TelemetryEnvironment environment;
    private final Supplier<PlayerCounts> playerCounts;
    private final Logger logger;

    private final AtomicBoolean failureAlreadyWarned = new AtomicBoolean();
    private final AtomicLong sentHeartbeats = new AtomicLong();

    public TelemetryService(
            SettingsService settings,
            TaskScheduler scheduler,
            TelemetryClient client,
            InstallationIdentity identity,
            TelemetryEnvironment environment,
            Supplier<PlayerCounts> playerCounts,
            Logger logger) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.client = Objects.requireNonNull(client, "client");
        this.identity = Objects.requireNonNull(identity, "identity");
        this.environment = Objects.requireNonNull(environment, "environment");
        this.playerCounts = Objects.requireNonNull(playerCounts, "playerCounts");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /**
     * Sends one heartbeat if telemetry is currently enabled and an endpoint
     * is configured; otherwise does nothing at all. Returns immediately - the
     * actual work is handed to {@link TaskScheduler}.
     */
    public void sendHeartbeat() {
        if (!isEnabled()) {
            return;
        }
        scheduler.runOnMainThread(() -> {
            PlayerCounts counts;
            try {
                counts = playerCounts.get();
            } catch (RuntimeException e) {
                reportFailure(e);
                return;
            }
            // Re-checked here as well: the main-thread hop means the setting
            // could have been reloaded to false in between.
            if (!isEnabled()) {
                return;
            }
            TelemetryPayload payload = TelemetryPayload.of(identity, environment, counts);
            scheduler.runAsync(() -> deliver(payload));
        });
    }

    /** Whether a heartbeat right now would result in an actual request. */
    public boolean isEnabled() {
        return client.isConfigured() && settings.get(CoreSettings.TELEMETRY_ENABLED);
    }

    /** Number of heartbeats successfully delivered since startup - for diagnostics and tests. */
    public long sentHeartbeats() {
        return sentHeartbeats.get();
    }

    private void deliver(TelemetryPayload payload) {
        try {
            client.send(payload);
            sentHeartbeats.incrementAndGet();
            logger.fine(() -> "Anonymous usage statistics heartbeat sent.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            reportFailure(e);
        }
    }

    /**
     * One warning per plugin run, everything after it at {@code FINE}. A
     * statistics endpoint being down is not the server owner's problem to
     * fix, so it must not produce a repeating warning every interval.
     */
    private void reportFailure(Exception e) {
        if (failureAlreadyWarned.compareAndSet(false, true)) {
            logger.warning(() -> "Could not send anonymous usage statistics ("
                    + e.getClass().getSimpleName() + ": " + e.getMessage()
                    + "). This does not affect the server; further occurrences are logged at FINE. "
                    + "Set telemetry.enabled: false in config.yml to switch statistics off.");
            return;
        }
        logger.log(Level.FINE, "Could not send anonymous usage statistics.", e);
    }
}

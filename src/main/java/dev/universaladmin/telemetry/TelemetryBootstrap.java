package dev.universaladmin.telemetry;

import dev.universaladmin.scheduler.TaskScheduler;
import dev.universaladmin.settings.CoreSettings;
import dev.universaladmin.settings.SettingsService;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.util.random.RandomGenerator;

/**
 * Wires the telemetry subsystem together and answers the one question the
 * rest of the plugin cares about: is anything being sent, and if not, why not.
 *
 * <p>Two possible outcomes, decided once at startup and logged once:
 *
 * <ol>
 *   <li>{@code telemetry.enabled: false} - nothing is constructed, no
 *       installation id is generated, no file is written, no thread is
 *       started. Off is genuinely off.
 *   <li>Enabled - installation id loaded (created on first use), an
 *       {@link HttpTelemetryClient} pointed at the fixed
 *       {@link #ENDPOINT official nicki41-telemetry instance}, and a
 *       {@link TelemetryScheduler} ticking in the background.
 * </ol>
 *
 * <p>The endpoint is not configurable: {@code config.yml} only ever
 * decides on/off ({@code telemetry.enabled}), never where a heartbeat goes.
 */
public final class TelemetryBootstrap implements AutoCloseable {

    /** The official, fixed nicki41-telemetry instance - a generic, multi-plugin backend, not UniversalAdmin-specific. See docs/user/telemetry.md. */
    public static final URI ENDPOINT = URI.create("https://telemetry.0nicki.de/v1/telemetry");

    private final TelemetryClient client;
    private final TelemetryService service;
    private final TelemetryScheduler scheduler;

    private TelemetryBootstrap(TelemetryClient client, TelemetryService service, TelemetryScheduler scheduler) {
        this.client = client;
        this.service = service;
        this.scheduler = scheduler;
    }

    /**
     * Builds and (where applicable) starts telemetry. Never throws: a problem
     * in here must never be able to take a server's plugin start down.
     *
     * @param dataFolder   plugin data folder, where {@code installation-id.yml} lives
     * @param environment  version information captured at startup
     * @param playerCounts reads the current player counts; called on the main thread
     */
    public static TelemetryBootstrap start(
            SettingsService settings,
            TaskScheduler taskScheduler,
            Path dataFolder,
            TelemetryEnvironment environment,
            Supplier<PlayerCounts> playerCounts,
            RandomGenerator random,
            Logger logger) {
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(taskScheduler, "taskScheduler");
        Objects.requireNonNull(dataFolder, "dataFolder");
        Objects.requireNonNull(environment, "environment");
        Objects.requireNonNull(playerCounts, "playerCounts");
        Objects.requireNonNull(random, "random");
        Objects.requireNonNull(logger, "logger");

        if (!settings.get(CoreSettings.TELEMETRY_ENABLED)) {
            logger.info("Anonymous usage statistics are disabled (telemetry.enabled: false); nothing is sent.");
            return inert();
        }

        InstallationIdentity identity = new InstallationIdentityStore(dataFolder, logger).loadOrCreate();
        Duration interval = settings.get(CoreSettings.TELEMETRY_INTERVAL);
        TelemetryClient client = new HttpTelemetryClient(ENDPOINT, environment.universalAdminVersion());
        TelemetryService service = new TelemetryService(
                settings, taskScheduler, client, identity, environment, playerCounts, logger);
        TelemetryScheduler scheduler = new TelemetryScheduler(service, interval, random, logger);
        scheduler.start();

        logger.info(() -> "Anonymous usage statistics enabled: a heartbeat is sent to " + ENDPOINT
                + " roughly every " + interval.toMinutes() + " minutes. "
                + "See docs/user/telemetry.md for the exact contents, or set telemetry.enabled: false to switch it off.");
        return new TelemetryBootstrap(client, service, scheduler);
    }

    /** Whether heartbeats are actually being sent. {@code false} in both no-op cases above. */
    public boolean isActive() {
        return scheduler != null;
    }

    /** The service, when telemetry is active - exposed for diagnostics; {@code null} otherwise. */
    public TelemetryService service() {
        return service;
    }

    @Override
    public void close() {
        if (scheduler != null) {
            scheduler.close();
        }
        if (client != null) {
            client.close();
        }
    }

    private static TelemetryBootstrap inert() {
        return new TelemetryBootstrap(new NoOpTelemetryClient(), null, null);
    }
}

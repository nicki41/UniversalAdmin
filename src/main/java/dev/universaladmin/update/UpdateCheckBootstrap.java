package dev.universaladmin.update;

import dev.universaladmin.localization.MessageService;
import dev.universaladmin.notification.NotificationService;
import dev.universaladmin.permission.PermissionNode;
import dev.universaladmin.scheduler.TaskScheduler;
import dev.universaladmin.settings.CoreSettings;
import dev.universaladmin.settings.SettingsService;
import java.time.Duration;
import java.util.Objects;
import java.util.logging.Logger;
import java.util.random.RandomGenerator;

/**
 * Wires the update-check subsystem together, the same two-outcome shape as
 * {@code telemetry.TelemetryBootstrap} (there is no "endpoint" concept here
 * - the repository is fixed - so there's no third outcome):
 *
 * <ol>
 *   <li>{@code update.check-for-updates: false} - nothing is constructed
 *       beyond a service that {@code /admin update} can still use for an
 *       explicit, on-demand check; no background timer runs.
 *   <li>Enabled (the default) - a {@link HttpGitHubReleaseClient} and a
 *       {@link UpdateCheckScheduler} ticking in the background.
 * </ol>
 *
 * <p>{@code update.check-for-updates} is a live setting (checked fresh by
 * {@link UpdateCheckService#checkNow()} every time, the same as {@code
 * telemetry.enabled}) but, also the same as telemetry, whether the
 * background timer exists at all is decided once here at startup - toggling
 * it from disabled to enabled via {@code /admin reload} takes effect for a
 * manual {@code /admin update} immediately, but the periodic background
 * check only starts ticking after a restart if it wasn't already running.
 */
public final class UpdateCheckBootstrap implements AutoCloseable {

    private final UpdateCheckService service;
    private final UpdateCheckScheduler scheduler;
    private final HttpGitHubReleaseClient client;

    private UpdateCheckBootstrap(UpdateCheckService service, UpdateCheckScheduler scheduler, HttpGitHubReleaseClient client) {
        this.service = service;
        this.scheduler = scheduler;
        this.client = client;
    }

    public static UpdateCheckBootstrap start(
            SettingsService settings,
            TaskScheduler taskScheduler,
            NotificationService notifications,
            MessageService messages,
            PermissionNode notifyPermission,
            String currentVersion,
            RandomGenerator random,
            Logger logger) {
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(taskScheduler, "taskScheduler");
        Objects.requireNonNull(notifications, "notifications");
        Objects.requireNonNull(messages, "messages");
        Objects.requireNonNull(notifyPermission, "notifyPermission");
        Objects.requireNonNull(currentVersion, "currentVersion");
        Objects.requireNonNull(random, "random");
        Objects.requireNonNull(logger, "logger");

        HttpGitHubReleaseClient client = new HttpGitHubReleaseClient("nicki41", "UniversalAdmin", currentVersion);
        UpdateCheckService service = new UpdateCheckService(
                settings, taskScheduler, client, notifications, messages, notifyPermission, currentVersion, logger);

        if (!settings.get(CoreSettings.UPDATE_CHECK_ENABLED)) {
            logger.info("Automatic update checks are disabled (update.check-for-updates: false); "
                    + "/admin update still works on demand.");
            return new UpdateCheckBootstrap(service, null, client);
        }

        Duration interval = settings.get(CoreSettings.UPDATE_CHECK_INTERVAL);
        UpdateCheckScheduler scheduler = new UpdateCheckScheduler(service, interval, random, logger);
        scheduler.start();
        logger.info(() -> "Update checks enabled: checking https://github.com/nicki41/UniversalAdmin/releases "
                + "roughly every " + interval.toHours() + " hours. Set update.check-for-updates: false in "
                + "config.yml to switch this off.");
        return new UpdateCheckBootstrap(service, scheduler, client);
    }

    public UpdateCheckService service() {
        return service;
    }

    @Override
    public void close() {
        if (scheduler != null) {
            scheduler.close();
        }
        client.close();
    }
}

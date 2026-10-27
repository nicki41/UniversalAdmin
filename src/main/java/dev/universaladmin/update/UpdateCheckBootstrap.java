package dev.universaladmin.update;

import dev.universaladmin.localization.MessageService;
import dev.universaladmin.notification.NotificationService;
import dev.universaladmin.permission.PermissionNode;
import dev.universaladmin.scheduler.TaskScheduler;
import dev.universaladmin.settings.CoreSettings;
import dev.universaladmin.settings.SettingsService;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.random.RandomGenerator;

/**
 * Wires the update-check subsystem together, the same two-outcome shape as
 * {@code telemetry.TelemetryBootstrap}:
 *
 * <ol>
 *   <li>{@code update.check-for-updates: false} - nothing is constructed
 *       beyond a service that {@code /admin update} and the join notice can
 *       still use; no background timer runs.
 *   <li>Enabled (the default) - an {@link UpdateCheckScheduler} ticking in
 *       the background over every {@link UpdateTargets target}.
 * </ol>
 *
 * <p>{@code update.check-for-updates} and {@code update.auto-download} are
 * live settings (read fresh on every check) but, the same as telemetry,
 * whether the background timer exists at all is decided once here at
 * startup - switching checks on via {@code /admin reload} takes effect for
 * {@code /admin update} immediately, the periodic check only after a
 * restart.
 */
public final class UpdateCheckBootstrap implements AutoCloseable {

    private final UpdateCheckService service;
    private final UpdateCheckScheduler scheduler;
    private final ReleaseClients clients;

    private UpdateCheckBootstrap(UpdateCheckService service, UpdateCheckScheduler scheduler, ReleaseClients clients) {
        this.service = service;
        this.scheduler = scheduler;
        this.clients = clients;
    }

    public static UpdateCheckBootstrap start(
            SettingsService settings,
            TaskScheduler taskScheduler,
            UpdateTargets targets,
            ReleaseClients clients,
            UpdateStager stager,
            NotificationService notifications,
            MessageService messages,
            PermissionNode notifyPermission,
            RandomGenerator random,
            Logger logger) {
        Objects.requireNonNull(random, "random");
        UpdateCheckService service = new UpdateCheckService(
                settings, taskScheduler, targets, clients, stager, notifications, messages, notifyPermission, logger);

        if (!settings.get(CoreSettings.UPDATE_CHECK_ENABLED)) {
            logger.info("Automatic update checks are disabled (update.check-for-updates: false); "
                    + "/admin update still works on demand.");
            return new UpdateCheckBootstrap(service, null, clients);
        }

        Duration interval = settings.get(CoreSettings.UPDATE_CHECK_INTERVAL);
        UpdateCheckScheduler scheduler = new UpdateCheckScheduler(service, interval, random, logger);
        scheduler.start();
        logger.info(() -> "Update checks enabled: checking https://github.com/" + UpdateTargets.CORE_REPOSITORY
                + "/releases (and the extensions' own) roughly every " + interval.toHours() + " hours"
                + (settings.get(CoreSettings.UPDATE_AUTO_DOWNLOAD)
                        ? "; new versions within the same major version are downloaded and applied at the next restart"
                        : "")
                + ". Set update.check-for-updates: false in config.yml to switch this off.");
        return new UpdateCheckBootstrap(service, scheduler, clients);
    }

    public UpdateCheckService service() {
        return service;
    }

    @Override
    public void close() {
        if (scheduler != null) {
            scheduler.close();
        }
        clients.close();
    }

    /** One {@link HttpGitHubReleaseClient} per repository, made on first use - shared by the check and {@code /admin update}. */
    public static final class ReleaseClients implements Function<String, GitHubReleaseClient>, AutoCloseable {

        private final String userAgentVersion;
        private final Map<String, HttpGitHubReleaseClient> clients = new ConcurrentHashMap<>();

        /** @param userAgentVersion UniversalAdmin's version, sent as its user agent */
        public ReleaseClients(String userAgentVersion) {
            this.userAgentVersion = userAgentVersion;
        }

        @Override
        public GitHubReleaseClient apply(String repository) {
            return clients.computeIfAbsent(repository, name -> new HttpGitHubReleaseClient(name, userAgentVersion));
        }

        @Override
        public void close() {
            clients.values().forEach(HttpGitHubReleaseClient::close);
            clients.clear();
        }
    }
}

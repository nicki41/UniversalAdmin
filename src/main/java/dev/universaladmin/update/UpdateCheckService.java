package dev.universaladmin.update;

import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.localization.MessageService;
import dev.universaladmin.notification.Notification;
import dev.universaladmin.notification.NotificationService;
import dev.universaladmin.permission.PermissionNode;
import dev.universaladmin.scheduler.TaskScheduler;
import dev.universaladmin.settings.CoreSettings;
import dev.universaladmin.settings.SettingsService;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Checks whether a newer UniversalAdmin release exists, and if so, tells
 * staff and the console about it. The <i>what</i>; {@link UpdateCheckScheduler}
 * owns the <i>when</i>, same split as {@code telemetry.TelemetryService}/
 * {@code TelemetryScheduler}.
 *
 * <p>Version comparison is a plain string inequality against the running
 * plugin version, not a full SemVer ordering - this project's releases only
 * ever move forward (see docs/release/releasing.md), so "the latest tag on
 * GitHub isn't what's currently running" is already the right signal in
 * every real scenario; it would only be wrong for a locally built jar newer
 * than anything published, which is a developer's own machine, not a
 * server this feature needs to protect.
 */
public final class UpdateCheckService {

    private final SettingsService settings;
    private final TaskScheduler scheduler;
    private final GitHubReleaseClient client;
    private final NotificationService notifications;
    private final MessageService messages;
    private final PermissionNode notifyPermission;
    private final String currentVersion;
    private final Logger logger;

    private final AtomicReference<GitHubRelease> latestKnown = new AtomicReference<>();
    private final AtomicBoolean failureAlreadyWarned = new AtomicBoolean();

    public UpdateCheckService(
            SettingsService settings,
            TaskScheduler scheduler,
            GitHubReleaseClient client,
            NotificationService notifications,
            MessageService messages,
            PermissionNode notifyPermission,
            String currentVersion,
            Logger logger) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.client = Objects.requireNonNull(client, "client");
        this.notifications = Objects.requireNonNull(notifications, "notifications");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.notifyPermission = Objects.requireNonNull(notifyPermission, "notifyPermission");
        this.currentVersion = Objects.requireNonNull(currentVersion, "currentVersion");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /**
     * Runs one check if update checking is currently enabled; otherwise does
     * nothing. Returns immediately - the actual request runs on a
     * background thread via {@link TaskScheduler}, notifications (if any)
     * are delivered back on the main thread.
     */
    public void checkNow() {
        if (!isEnabled()) {
            return;
        }
        scheduler.runAsync(() -> {
            try {
                GitHubRelease release = client.fetchLatest();
                latestKnown.set(release);
                failureAlreadyWarned.set(false);
                if (!release.version().equals(currentVersion)) {
                    scheduler.runOnMainThread(() -> announce(release));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                reportFailure(e);
            }
        });
    }

    /** Whether a check right now would actually run - {@code update.check-for-updates} in {@code config.yml}. */
    public boolean isEnabled() {
        return settings.get(CoreSettings.UPDATE_CHECK_ENABLED);
    }

    /** The most recently fetched release, if any check has completed since startup - what {@code /admin update} acts on without a redundant fetch. */
    public Optional<GitHubRelease> latestKnown() {
        return Optional.ofNullable(latestKnown.get());
    }

    public String currentVersion() {
        return currentVersion;
    }

    private void announce(GitHubRelease release) {
        String text = messages.get(MessageKey.of("update.available"), release.version(), currentVersion);
        logger.warning(() -> "A new UniversalAdmin version is available: " + release.version()
                + " (currently running " + currentVersion + "). Run /admin update to download it, then restart "
                + "the server to apply it. " + release.htmlUrl());
        notifications.notifyStaff(notifyPermission, Notification.info(text));
    }

    /**
     * One warning per run of failures, everything after it at {@code FINE} -
     * same reasoning as {@code TelemetryService#reportFailure}: a GitHub
     * outage or rate limit is not the server owner's problem to fix, and
     * must not fill the log with a repeating warning every interval. Reset
     * on the next successful check, so a later, separate outage still warns
     * once of its own.
     */
    private void reportFailure(Exception e) {
        if (failureAlreadyWarned.compareAndSet(false, true)) {
            logger.warning(() -> "Could not check for a new UniversalAdmin version ("
                    + e.getClass().getSimpleName() + ": " + e.getMessage()
                    + "). This does not affect the server; further occurrences are logged at FINE. "
                    + "Set update.check-for-updates: false in config.yml to stop trying.");
            return;
        }
        logger.log(Level.FINE, "Could not check for a new UniversalAdmin version.", e);
    }
}

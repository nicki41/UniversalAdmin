package dev.universaladmin.update;

import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.localization.MessageService;
import dev.universaladmin.notification.Notification;
import dev.universaladmin.notification.NotificationService;
import dev.universaladmin.permission.PermissionNode;
import dev.universaladmin.scheduler.TaskScheduler;
import dev.universaladmin.settings.CoreSettings;
import dev.universaladmin.settings.SettingsService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Checks whether newer releases exist - of UniversalAdmin and of every
 * extension that registered an update source ({@link UpdateTargets}) - and
 * tells staff and the console about them. With {@code update.auto-download}
 * a newer release within the same major version is downloaded into
 * {@code plugins/update/} right away and applied at the next restart. The
 * <i>what</i>; {@link UpdateCheckScheduler} owns the <i>when</i>, same split
 * as {@code telemetry.TelemetryService}/{@code TelemetryScheduler}.
 *
 * <p>Only a release that is actually newer by SemVer ordering counts (see
 * {@link SemanticVersion}) - a server running a newer build than the latest
 * GitHub release is never told to "update" to an older one. Every check
 * repeats the notice while an update is pending; staff who join later get
 * it from {@link #pendingNotices()} (see {@code UpdateJoinListener}).
 */
public final class UpdateCheckService {

    private final SettingsService settings;
    private final TaskScheduler scheduler;
    private final UpdateTargets targets;
    private final Function<String, GitHubReleaseClient> clients;
    private final UpdateStager stager;
    private final NotificationService notifications;
    private final MessageService messages;
    private final PermissionNode notifyPermission;
    private final Logger logger;

    /** The latest release seen per target name. */
    private final Map<String, GitHubRelease> latestKnown = new ConcurrentHashMap<>();
    /** The version already downloaded into plugins/update/ per target name. */
    private final Map<String, String> staged = new ConcurrentHashMap<>();
    private final AtomicBoolean failureAlreadyWarned = new AtomicBoolean();

    /**
     * @param clients one release client per repository ({@code "owner/name"})
     * @param stager  downloads and stages a release's jar
     */
    public UpdateCheckService(
            SettingsService settings,
            TaskScheduler scheduler,
            UpdateTargets targets,
            Function<String, GitHubReleaseClient> clients,
            UpdateStager stager,
            NotificationService notifications,
            MessageService messages,
            PermissionNode notifyPermission,
            Logger logger) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.targets = Objects.requireNonNull(targets, "targets");
        this.clients = Objects.requireNonNull(clients, "clients");
        this.stager = Objects.requireNonNull(stager, "stager");
        this.notifications = Objects.requireNonNull(notifications, "notifications");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.notifyPermission = Objects.requireNonNull(notifyPermission, "notifyPermission");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /**
     * Runs one check of every target if update checking is currently enabled;
     * otherwise does nothing. Returns immediately - the requests and any
     * download run on a background thread via {@link TaskScheduler},
     * notifications are delivered back on the main thread.
     */
    public void checkNow() {
        if (!isEnabled()) {
            return;
        }
        scheduler.runAsync(() -> {
            for (UpdateTarget target : targets.all()) {
                if (!check(target)) {
                    return;
                }
            }
        });
    }

    /** @return {@code false} if the thread was interrupted - the remaining targets are skipped */
    private boolean check(UpdateTarget target) {
        try {
            GitHubRelease release = clients.apply(target.repository()).fetchLatest();
            latestKnown.put(target.name(), release);
            failureAlreadyWarned.set(false);
            if (!SemanticVersion.isNewer(release.version(), target.currentVersion())) {
                return true;
            }
            if (isAutoDownload() && SemanticVersion.sameMajor(release.version(), target.currentVersion())
                    && !release.version().equals(staged.get(target.name()))) {
                download(target, release);
            }
            scheduler.runOnMainThread(() -> announce(target, release));
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            reportFailure(target, e);
            return true;
        }
    }

    private void download(UpdateTarget target, GitHubRelease release) throws InterruptedException {
        try {
            stager.stage(target, release);
            staged.put(target.name(), release.version());
            logger.info(() -> target.name() + " " + release.version() + " was downloaded into plugins/update/ and is "
                    + "applied at the next restart.");
        } catch (java.io.IOException e) {
            logger.warning(() -> "Could not download " + target.name() + " " + release.version() + " automatically ("
                    + e.getMessage() + "). Run /admin update to try again.");
        }
    }

    /** Whether a check right now would actually run - {@code update.check-for-updates} in {@code config.yml}. */
    public boolean isEnabled() {
        return settings.get(CoreSettings.UPDATE_CHECK_ENABLED);
    }

    /** Whether newer releases are downloaded on their own - {@code update.auto-download}. */
    public boolean isAutoDownload() {
        return settings.get(CoreSettings.UPDATE_AUTO_DOWNLOAD);
    }

    /** The most recently fetched release of a target, if a check of it has completed since startup. */
    public Optional<GitHubRelease> latestKnown(String targetName) {
        return Optional.ofNullable(latestKnown.get(targetName));
    }

    /** Records that {@code /admin update} staged {@code version} of a target, so the notices say "restart". */
    public void markStaged(String targetName, String version) {
        staged.put(targetName, version);
    }

    /** The notice for every target with a newer release - for staff who join after the check. */
    public List<String> pendingNotices() {
        List<String> notices = new ArrayList<>();
        for (UpdateTarget target : targets.all()) {
            GitHubRelease release = latestKnown.get(target.name());
            if (release != null && SemanticVersion.isNewer(release.version(), target.currentVersion())) {
                notices.add(notice(target, release));
            }
        }
        return notices;
    }

    private String notice(UpdateTarget target, GitHubRelease release) {
        boolean downloaded = release.version().equals(staged.get(target.name()));
        return messages.get(MessageKey.of(downloaded ? "update.downloaded" : "update.available"),
                release.version(), target.currentVersion(), target.name());
    }

    private void announce(UpdateTarget target, GitHubRelease release) {
        boolean downloaded = release.version().equals(staged.get(target.name()));
        logger.warning(() -> downloaded
                ? target.name() + " " + release.version() + " is downloaded and waits in plugins/update/ (currently running "
                        + target.currentVersion() + "). Restart the server to apply it."
                : "A new version of " + target.name() + " is available: " + release.version() + " (currently running "
                        + target.currentVersion() + "). Run /admin update to download it, then restart the server to "
                        + "apply it. " + release.htmlUrl());
        notifications.notifyStaff(notifyPermission, Notification.info(notice(target, release)));
    }

    /**
     * One warning per run of failures, everything after it at {@code FINE} -
     * same reasoning as {@code TelemetryService#reportFailure}: a GitHub
     * outage or rate limit is not the server owner's problem to fix, and
     * must not fill the log with a repeating warning every interval. Reset
     * on the next successful check, so a later, separate outage still warns
     * once of its own.
     */
    private void reportFailure(UpdateTarget target, Exception e) {
        if (failureAlreadyWarned.compareAndSet(false, true)) {
            logger.warning(() -> "Could not check for a new version of " + target.name() + " ("
                    + e.getClass().getSimpleName() + ": " + e.getMessage()
                    + "). This does not affect the server; further occurrences are logged at FINE. "
                    + "Set update.check-for-updates: false in config.yml to stop trying.");
            return;
        }
        logger.log(Level.FINE, "Could not check for a new version of " + target.name() + ".", e);
    }
}

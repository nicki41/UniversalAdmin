package dev.universaladmin.update;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.universaladmin.localization.MessageService;
import dev.universaladmin.notification.Notification;
import dev.universaladmin.notification.NotificationService;
import dev.universaladmin.permission.PermissionNode;
import dev.universaladmin.settings.CoreSettings;
import dev.universaladmin.settings.SettingRegistry;
import dev.universaladmin.settings.SettingsService;
import dev.universaladmin.settings.YamlSettingsService;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/**
 * The behavioural guarantees an update check makes: off means no request at
 * all, a GitHub outage is a non-event, a matching version stays quiet, a
 * newer one notifies staff - and is downloaded on its own within the same
 * major version - for UniversalAdmin and every extension with an update
 * source.
 */
class UpdateCheckServiceTest {

    private static final Logger LOGGER = Logger.getLogger("update-check-test");
    private static final PermissionNode NOTIFY_PERMISSION = PermissionNode.core("update.notify");
    private static final MessageService MESSAGES = (key, args) -> key.value() + ":" + args[2] + ":" + args[0];

    private final AtomicReference<YamlConfiguration> config = new AtomicReference<>(configOf(""));
    private final InlineTaskScheduler scheduler = new InlineTaskScheduler();
    private final RecordingNotificationService notifications = new RecordingNotificationService();
    private final List<String> staged = new ArrayList<>();
    private final UpdateStager stager = (target, release) -> staged.add(target.name() + " " + release.version());
    private final UpdateTargets targets = new UpdateTargets();

    private SettingsService settings() {
        SettingRegistry registry = new SettingRegistry();
        CoreSettings.registerAll(registry);
        return new YamlSettingsService(registry, config::get, LOGGER);
    }

    private UpdateCheckService serviceWith(Function<String, GitHubReleaseClient> clients, SettingsService settings) {
        return new UpdateCheckService(settings, scheduler, targets, clients, stager, notifications, MESSAGES, NOTIFY_PERMISSION,
                LOGGER);
    }

    private void core(String version) {
        targets.add(new UpdateTarget("UniversalAdmin", UpdateTargets.CORE_REPOSITORY, UpdateTargets.CORE_ASSET_PREFIX, version,
                Path.of("UniversalAdmin.jar")));
    }

    private static YamlConfiguration configOf(String yaml) {
        YamlConfiguration configuration = new YamlConfiguration();
        try {
            configuration.loadFromString(yaml);
        } catch (InvalidConfigurationException e) {
            throw new IllegalStateException(e);
        }
        return configuration;
    }

    private static Function<String, GitHubReleaseClient> fixedRelease(String tagName) {
        return repository -> () -> new GitHubRelease(tagName, "https://example.com/" + tagName, false, List.of());
    }

    @Test
    void downloadsANewerVersionOfTheSameMajorOnceAndSaysRestart() {
        core("1.5.0");
        UpdateCheckService service = serviceWith(fixedRelease("v1.6.0"), settings());

        service.checkNow();
        service.checkNow();

        assertEquals(List.of("UniversalAdmin 1.6.0"), staged, "downloaded once, not on every check");
        assertEquals(2, notifications.notified().size(), "every check repeats the notice");
        assertEquals(List.of("update.downloaded:UniversalAdmin:1.6.0"), service.pendingNotices());
    }

    @Test
    void aNewMajorVersionWaitsForAStaffMember() {
        core("1.5.0");
        UpdateCheckService service = serviceWith(fixedRelease("v2.0.0"), settings());

        service.checkNow();

        assertTrue(staged.isEmpty());
        assertEquals(List.of("update.available:UniversalAdmin:2.0.0"), service.pendingNotices());
    }

    @Test
    void withoutAutoDownloadOnlyNotifies() {
        config.set(configOf("update:\n  auto-download: false\n"));
        core("1.5.0");
        UpdateCheckService service = serviceWith(fixedRelease("v1.6.0"), settings());

        service.checkNow();

        assertTrue(staged.isEmpty());
        assertEquals(1, notifications.notified().size());
        assertEquals("1.6.0", service.latestKnown("UniversalAdmin").orElseThrow().version());
    }

    @Test
    void extensionsWithAnUpdateSourceAreCheckedFromTheirOwnRepository() {
        core("1.6.0");
        targets.add(new UpdateTarget("UniversalAdminDiscord", "universaladmin-mc/universaladmin-discord", "universaladmin-discord-",
                "1.5.0", Path.of("universaladmin-discord-1.5.0.jar")));
        Map<String, String> latest = Map.of(UpdateTargets.CORE_REPOSITORY, "v1.6.0",
                "universaladmin-mc/universaladmin-discord", "v1.6.1");
        UpdateCheckService service = serviceWith(repository -> () -> new GitHubRelease(latest.get(repository),
                "https://example.com", false, List.of()), settings());

        service.checkNow();

        assertEquals(List.of("UniversalAdminDiscord 1.6.1"), staged);
        assertEquals(List.of("update.downloaded:UniversalAdminDiscord:1.6.1"), service.pendingNotices());
    }

    @Test
    void staysQuietWhenAlreadyOnTheLatestVersion() {
        core("0.1.0-alpha");
        UpdateCheckService service = serviceWith(fixedRelease("v0.1.0-alpha"), settings());

        service.checkNow();

        assertTrue(notifications.notified().isEmpty());
        assertTrue(service.pendingNotices().isEmpty());
        assertEquals("0.1.0-alpha", service.latestKnown("UniversalAdmin").orElseThrow().version());
    }

    @Test
    void disabledMeansZeroRequestsAndZeroWork() {
        config.set(configOf("update:\n  check-for-updates: false\n"));
        core("0.1.0-alpha");
        AtomicRequestCounter counter = new AtomicRequestCounter();
        UpdateCheckService service = serviceWith(repository -> counter, settings());

        service.checkNow();
        service.checkNow();

        assertFalse(service.isEnabled());
        assertEquals(0, counter.requests());
        assertEquals(0, scheduler.mainThreadHops());
        assertEquals(0, scheduler.asyncTasks());
        assertTrue(notifications.notified().isEmpty());
    }

    @Test
    void aFailingRequestNeverPropagatesAndLeavesNoLatestKnownRelease() {
        core("0.1.0-alpha");
        UpdateCheckService service = serviceWith(repository -> () -> {
            throw new IOException("connection refused");
        }, settings());

        assertDoesNotThrow(service::checkNow);
        assertDoesNotThrow(service::checkNow);

        assertTrue(service.latestKnown("UniversalAdmin").isEmpty());
        assertTrue(notifications.notified().isEmpty());
    }

    @Test
    void turningItOffAtRuntimeStopsTheNextCheck() {
        core("0.1.0-alpha");
        SettingsService settings = settings();
        UpdateCheckService service = serviceWith(fixedRelease("v0.2.0"), settings);

        service.checkNow();
        assertEquals(1, notifications.notified().size());

        config.set(configOf("update:\n  check-for-updates: false\n"));
        settings.reload();
        service.checkNow();

        assertEquals(1, notifications.notified().size(), "no further check after check-for-updates: false");
    }

    private static final class AtomicRequestCounter implements GitHubReleaseClient {
        private int requests;

        @Override
        public GitHubRelease fetchLatest() {
            requests++;
            return new GitHubRelease("v0.2.0", "https://example.com", false, List.of());
        }

        int requests() {
            return requests;
        }
    }

    private static final class RecordingNotificationService implements NotificationService {
        private final List<Notification> notified = new ArrayList<>();

        @Override
        public void notifyPlayer(UUID playerId, Notification notification) {
        }

        @Override
        public void notifyStaff(PermissionNode requiredPermission, Notification notification) {
            notified.add(notification);
        }

        @Override
        public void broadcast(Component message) {
        }

        @Override
        public void broadcastTitle(Component title, Component subtitle, Duration fadeIn, Duration stay, Duration fadeOut) {
        }

        @Override
        public void broadcastActionBar(Component message) {
        }

        List<Notification> notified() {
            return notified;
        }
    }
}

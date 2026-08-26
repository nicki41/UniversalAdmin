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
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/**
 * The behavioural guarantees an update check makes: off means no request at
 * all, a GitHub outage is a non-event, a matching version stays quiet, and a
 * different one notifies staff exactly once per check.
 */
class UpdateCheckServiceTest {

    private static final Logger LOGGER = Logger.getLogger("update-check-test");
    private static final PermissionNode NOTIFY_PERMISSION = PermissionNode.core("update.notify");
    private static final MessageService MESSAGES = (key, args) -> key.value();

    private final AtomicReference<YamlConfiguration> config = new AtomicReference<>(configOf(""));
    private final InlineTaskScheduler scheduler = new InlineTaskScheduler();
    private final RecordingNotificationService notifications = new RecordingNotificationService();

    private SettingsService settings() {
        SettingRegistry registry = new SettingRegistry();
        CoreSettings.registerAll(registry);
        return new YamlSettingsService(registry, config::get, LOGGER);
    }

    private UpdateCheckService serviceWith(GitHubReleaseClient client, SettingsService settings, String currentVersion) {
        return new UpdateCheckService(settings, scheduler, client, notifications, MESSAGES, NOTIFY_PERMISSION, currentVersion, LOGGER);
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

    private static GitHubReleaseClient fixedRelease(String tagName) {
        return () -> new GitHubRelease(tagName, "https://example.com/" + tagName, false, List.of());
    }

    @Test
    void notifiesStaffWhenALaterVersionExists() {
        UpdateCheckService service = serviceWith(fixedRelease("v0.2.0"), settings(), "0.1.0-alpha");

        service.checkNow();

        assertEquals(1, notifications.notified().size());
        assertEquals("0.2.0", service.latestKnown().orElseThrow().version());
    }

    @Test
    void staysQuietWhenAlreadyOnTheLatestVersion() {
        UpdateCheckService service = serviceWith(fixedRelease("v0.1.0-alpha"), settings(), "0.1.0-alpha");

        service.checkNow();

        assertTrue(notifications.notified().isEmpty());
        assertEquals("0.1.0-alpha", service.latestKnown().orElseThrow().version());
    }

    @Test
    void disabledMeansZeroRequestsAndZeroWork() {
        config.set(configOf("update:\n  check-for-updates: false\n"));
        AtomicRequestCounter counter = new AtomicRequestCounter();
        UpdateCheckService service = serviceWith(counter, settings(), "0.1.0-alpha");

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
        UpdateCheckService service = serviceWith(() -> {
            throw new IOException("connection refused");
        }, settings(), "0.1.0-alpha");

        assertDoesNotThrow(service::checkNow);
        assertDoesNotThrow(service::checkNow);

        assertTrue(service.latestKnown().isEmpty());
        assertTrue(notifications.notified().isEmpty());
    }

    @Test
    void turningItOffAtRuntimeStopsTheNextCheck() {
        SettingsService settings = settings();
        UpdateCheckService service = serviceWith(fixedRelease("v0.2.0"), settings, "0.1.0-alpha");

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

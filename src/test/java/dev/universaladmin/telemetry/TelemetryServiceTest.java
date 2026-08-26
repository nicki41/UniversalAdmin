package dev.universaladmin.telemetry;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.universaladmin.settings.CoreSettings;
import dev.universaladmin.settings.SettingRegistry;
import dev.universaladmin.settings.SettingsService;
import dev.universaladmin.settings.YamlSettingsService;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.logging.Logger;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/**
 * The behavioural guarantees telemetry makes to a server owner: off means no
 * request at all, a broken endpoint is a non-event, and nothing ever runs
 * blocking work on the main thread.
 *
 * <p>No test here performs a real network request - {@link
 * RecordingTelemetryClient} stands in for the HTTP client throughout.
 */
class TelemetryServiceTest {

    private static final Logger LOGGER = Logger.getLogger("telemetry-test");
    private static final InstallationIdentity IDENTITY =
            new InstallationIdentity("0123456789abcdef0123456789abcdef");
    private static final TelemetryEnvironment ENVIRONMENT =
            new TelemetryEnvironment("0.1.0-alpha", "1.21.4", 25);

    private final AtomicReference<YamlConfiguration> config = new AtomicReference<>(configOf(""));
    private final InlineTaskScheduler scheduler = new InlineTaskScheduler();

    private SettingsService settings() {
        SettingRegistry registry = new SettingRegistry();
        CoreSettings.registerAll(registry);
        return new YamlSettingsService(registry, config::get, LOGGER);
    }

    private TelemetryService serviceWith(TelemetryClient client, SettingsService settings) {
        return serviceWith(client, settings, () -> new PlayerCounts(17, 100));
    }

    private TelemetryService serviceWith(
            TelemetryClient client, SettingsService settings, Supplier<PlayerCounts> counts) {
        return new TelemetryService(settings, scheduler, client, IDENTITY, ENVIRONMENT, counts, LOGGER);
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

    @Test
    void sendsOneHeartbeatWithTheCurrentCountsWhenEnabled() {
        RecordingTelemetryClient client = new RecordingTelemetryClient();

        serviceWith(client, settings()).sendHeartbeat();

        assertEquals(1, client.sent().size());
        TelemetryPayload payload = client.sent().getFirst();
        assertEquals(IDENTITY.value(), payload.installationId());
        assertEquals(TelemetryPayload.PLUGIN_ID, payload.pluginId());
        assertEquals("0.1.0-alpha", payload.pluginVersion());
        assertEquals("1.21.4", payload.minecraftVersion());
        assertEquals(25, payload.javaMajorVersion());
        assertEquals(17, payload.onlinePlayers());
        assertEquals(100, payload.maxPlayers());
    }

    @Test
    void disabledMeansZeroRequestsAndZeroWork() {
        config.set(configOf("telemetry:\n  enabled: false\n"));
        RecordingTelemetryClient client = new RecordingTelemetryClient();
        TelemetryService service = serviceWith(client, settings());

        service.sendHeartbeat();
        service.sendHeartbeat();
        service.sendHeartbeat();

        assertFalse(service.isEnabled());
        assertTrue(client.sent().isEmpty(), "a disabled subsystem must not build or send anything");
        // Not even a main-thread hop to read player counts: off is genuinely off,
        // there is no "essential" second path.
        assertEquals(0, scheduler.mainThreadHops());
        assertEquals(0, scheduler.asyncTasks());
    }

    @Test
    void withoutAConfiguredEndpointNothingIsSentAnywhere() {
        TelemetryService service = serviceWith(new NoOpTelemetryClient(), settings());

        service.sendHeartbeat();

        assertFalse(service.isEnabled());
        assertEquals(0, scheduler.mainThreadHops());
        assertEquals(0, service.sentHeartbeats());
    }

    @Test
    void turningItOffAtRuntimeStopsTheNextHeartbeat() {
        RecordingTelemetryClient client = new RecordingTelemetryClient();
        SettingsService settings = settings();
        TelemetryService service = serviceWith(client, settings);

        service.sendHeartbeat();
        assertEquals(1, client.sent().size());

        // Same thing /admin reload does: swap the file contents, then reload.
        config.set(configOf("telemetry:\n  enabled: false\n"));
        settings.reload();
        service.sendHeartbeat();

        assertEquals(1, client.sent().size(), "no further heartbeat after telemetry.enabled: false");
    }

    @Test
    void aFailingEndpointNeverPropagatesAndNeverCountsAsSent() {
        RecordingTelemetryClient client = new RecordingTelemetryClient(new IOException("connection refused"));
        TelemetryService service = serviceWith(client, settings());

        assertDoesNotThrow(service::sendHeartbeat);
        assertDoesNotThrow(service::sendHeartbeat);

        assertEquals(2, client.sent().size(), "both attempts reached the client");
        assertEquals(0, service.sentHeartbeats(), "neither attempt succeeded");
    }

    @Test
    void aFailureWhileReadingPlayerCountsIsSwallowedToo() {
        RecordingTelemetryClient client = new RecordingTelemetryClient();
        TelemetryService service = serviceWith(client, settings(), () -> {
            throw new IllegalStateException("server not ready");
        });

        assertDoesNotThrow(service::sendHeartbeat);
        assertTrue(client.sent().isEmpty());
    }

    @Test
    void readsPlayerCountsOnTheMainThreadAndSendsOffIt() {
        RecordingTelemetryClient client = new RecordingTelemetryClient();

        serviceWith(client, settings()).sendHeartbeat();

        // One hop onto the main thread (to read Bukkit state), one background
        // task (to do the blocking IO) - see docs/architecture/threading.md.
        assertEquals(1, scheduler.mainThreadHops());
        assertEquals(1, scheduler.asyncTasks());
    }
}

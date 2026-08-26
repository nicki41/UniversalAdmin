package dev.universaladmin.telemetry;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.universaladmin.settings.CoreSettings;
import dev.universaladmin.settings.SettingRegistry;
import dev.universaladmin.settings.SettingsService;
import dev.universaladmin.settings.YamlSettingsService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;
import java.util.random.RandomGenerator;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The three startup outcomes described on {@link TelemetryBootstrap}, checked
 * end to end from a {@code config.yml} snippet.
 *
 * <p>The "configured endpoint" case points at a port nothing listens on and
 * is closed immediately: the first heartbeat is minutes away
 * ({@link TelemetryScheduler#INITIAL_DELAY}), so no test here ever opens a
 * connection.
 */
class TelemetryBootstrapTest {

    private static final Logger LOGGER = Logger.getLogger("telemetry-test");
    private static final TelemetryEnvironment ENVIRONMENT =
            new TelemetryEnvironment("0.1.0-alpha", "1.21.4", 25);

    private static SettingsService settingsFrom(String yaml) {
        YamlConfiguration configuration = new YamlConfiguration();
        try {
            configuration.loadFromString(yaml);
        } catch (InvalidConfigurationException e) {
            throw new IllegalStateException(e);
        }
        SettingRegistry registry = new SettingRegistry();
        CoreSettings.registerAll(registry);
        return new YamlSettingsService(registry, () -> configuration, LOGGER);
    }

    private static TelemetryBootstrap start(String yaml, Path dataFolder) {
        return TelemetryBootstrap.start(
                settingsFrom(yaml),
                new InlineTaskScheduler(),
                dataFolder,
                ENVIRONMENT,
                () -> new PlayerCounts(3, 20),
                RandomGenerator.getDefault(),
                LOGGER);
    }

    @Test
    void disabledStartsNothingAndWritesNoInstallationId(@TempDir Path dataFolder) {
        try (TelemetryBootstrap telemetry = start("telemetry:\n  enabled: false\n", dataFolder)) {
            assertFalse(telemetry.isActive());
            assertNull(telemetry.service());
        }

        assertFalse(Files.exists(dataFolder.resolve(InstallationIdentityStore.FILE_NAME)),
                "a disabled installation must not even generate an id");
    }

    @Test
    void enabledWithoutAnEndpointStaysInertAndWritesNoInstallationId(@TempDir Path dataFolder) {
        // A server owner who explicitly clears the default endpoint - the
        // shipped default itself now has a real one, see CoreSettings.
        try (TelemetryBootstrap telemetry = start("telemetry:\n  enabled: true\n  endpoint: \"\"\n", dataFolder)) {
            assertFalse(telemetry.isActive());
        }

        assertFalse(Files.exists(dataFolder.resolve(InstallationIdentityStore.FILE_NAME)));
    }

    @Test
    void theShippedDefaultIsActiveAgainstTheOfficialEndpoint(@TempDir Path dataFolder) {
        // No config.yml override at all - exactly what a fresh install runs with.
        try (TelemetryBootstrap telemetry = start("", dataFolder)) {
            assertTrue(telemetry.isActive());
            assertTrue(telemetry.service().isEnabled());
        }
    }

    @Test
    void anUnusableEndpointFallsBackToSendingNothing(@TempDir Path dataFolder) {
        try (TelemetryBootstrap telemetry = start("telemetry:\n  endpoint: \"ftp://example.invalid/x\"\n", dataFolder)) {
            assertFalse(telemetry.isActive(), "a non-http endpoint must not silently become some other host");
        }

        assertFalse(Files.exists(dataFolder.resolve(InstallationIdentityStore.FILE_NAME)));
    }

    @Test
    void aConfiguredEndpointStartsTheHeartbeatAndCreatesAnInstallationId(@TempDir Path dataFolder) {
        try (TelemetryBootstrap telemetry =
                     start("telemetry:\n  endpoint: \"http://127.0.0.1:1/telemetry\"\n", dataFolder)) {
            assertTrue(telemetry.isActive());
            assertTrue(telemetry.service().isEnabled());
            assertTrue(Files.isRegularFile(dataFolder.resolve(InstallationIdentityStore.FILE_NAME)));
        }
    }

    @Test
    void closeIsSafeInEveryOutcomeAndSafeToRepeat(@TempDir Path dataFolder) {
        TelemetryBootstrap inert = start("telemetry:\n  enabled: false\n", dataFolder);
        TelemetryBootstrap active =
                start("telemetry:\n  endpoint: \"http://127.0.0.1:1/telemetry\"\n", dataFolder);

        // onDisable must never throw over statistics, however telemetry ended
        // up configured - including when close() runs twice.
        assertDoesNotThrow(() -> {
            inert.close();
            inert.close();
            active.close();
            active.close();
        });
    }
}

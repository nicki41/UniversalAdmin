package dev.universaladmin.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/**
 * Exercises {@link YamlSettingsService} against real (but in-memory)
 * {@link YamlConfiguration} instances - no Paper server needed, since
 * {@code YamlConfiguration} is a plain data structure class in paper-api.
 */
class YamlSettingsServiceTest {

    private static final SettingKey<Integer> PAGE_SIZE = SettingKey.of("core", "gui.page-size");
    private static final SettingKey<String> HOST = SettingKey.of("core", "database.host");

    private SettingRegistry registryWithDefaults() {
        SettingRegistry registry = new SettingRegistry();
        registry.register(SettingDefinition.builder(PAGE_SIZE, SettingTypes.INTEGER, 45)
                .validator(SettingValidators.intRange(9, 54))
                .build());
        registry.register(SettingDefinition.builder(HOST, SettingTypes.STRING, "localhost")
                .requiresRestart(true)
                .build());
        return registry;
    }

    private YamlConfiguration configOf(String yaml) {
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.loadFromString(yaml);
        } catch (InvalidConfigurationException e) {
            throw new IllegalStateException(e);
        }
        return config;
    }

    @Test
    void usesTheDefaultWhenTheKeyIsAbsent() {
        YamlSettingsService service =
                new YamlSettingsService(registryWithDefaults(), () -> configOf(""), Logger.getLogger("test"));

        assertEquals(45, service.get(PAGE_SIZE));
    }

    @Test
    void fallsBackToDefaultAndReportsAnErrorOnAnUnparsableValue() {
        AtomicReference<YamlConfiguration> current = new AtomicReference<>(configOf("gui:\n  page-size: 45\n"));
        YamlSettingsService service =
                new YamlSettingsService(registryWithDefaults(), current::get, Logger.getLogger("test"));

        current.set(configOf("gui:\n  page-size: not-a-number\n"));
        ConfigReloadResult result = service.reload();

        assertEquals(45, service.get(PAGE_SIZE));
        assertFalse(result.validationErrors().isEmpty());
        assertTrue(result.validationErrors().get(0).contains("page-size"));
    }

    @Test
    void fallsBackToDefaultWhenTheValueFailsItsValidator() {
        AtomicReference<YamlConfiguration> current = new AtomicReference<>(configOf(""));
        YamlSettingsService service =
                new YamlSettingsService(registryWithDefaults(), current::get, Logger.getLogger("test"));

        current.set(configOf("gui:\n  page-size: 3\n")); // below the registered min of 9
        ConfigReloadResult result = service.reload();

        assertEquals(45, service.get(PAGE_SIZE));
        assertFalse(result.validationErrors().isEmpty());
    }

    @Test
    void appliesAChangedValueForASettingThatDoesNotRequireRestart() {
        AtomicReference<YamlConfiguration> current = new AtomicReference<>(configOf(""));
        YamlSettingsService service =
                new YamlSettingsService(registryWithDefaults(), current::get, Logger.getLogger("test"));
        assertEquals(45, service.get(PAGE_SIZE));

        current.set(configOf("gui:\n  page-size: 27\n"));
        ConfigReloadResult result = service.reload();

        assertEquals(27, service.get(PAGE_SIZE));
        assertTrue(result.applied().contains(PAGE_SIZE));
    }

    @Test
    void keepsThePreviousValueForASettingThatRequiresRestart() {
        AtomicReference<YamlConfiguration> current =
                new AtomicReference<>(configOf("database:\n  host: original-host\n"));
        YamlSettingsService service =
                new YamlSettingsService(registryWithDefaults(), current::get, Logger.getLogger("test"));
        assertEquals("original-host", service.get(HOST));

        current.set(configOf("database:\n  host: changed-host\n"));
        ConfigReloadResult result = service.reload();

        assertEquals("original-host", service.get(HOST));
        assertTrue(result.pendingRestart().contains(HOST));
    }
}

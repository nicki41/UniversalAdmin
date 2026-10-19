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

    @Test
    void settingsRegisteredAfterConstructionAreReadFromTheFile() {
        SettingRegistry registry = registryWithDefaults();
        YamlSettingsService service =
                new YamlSettingsService(registry, () -> configOf("late:\n  value: 42\n"), Logger.getLogger("test"));
        SettingKey<Integer> late = SettingKey.of("module", "late.value");
        registry.register(SettingDefinition.builder(late, SettingTypes.INTEGER, 1).build());

        assertEquals(42, service.get(late));
    }

    @Test
    void setPersistsAParsedValidatedValueAndAppliesItLive() throws SettingParseException {
        SettingRegistry registry = new SettingRegistry();
        SettingKey<java.time.Duration> interval = SettingKey.of("module", "sync.interval");
        registry.register(SettingDefinition.builder(interval, SettingTypes.DURATION, java.time.Duration.ofMinutes(5)).build());
        YamlConfiguration config = new YamlConfiguration();
        java.util.concurrent.atomic.AtomicInteger saves = new java.util.concurrent.atomic.AtomicInteger();
        YamlSettingsService service = new YamlSettingsService(registry, () -> config, saves::incrementAndGet, Logger.getLogger("test"));

        SettingChange change = service.set("sync.interval", "2h");

        assertEquals("5m", change.oldValue());
        assertEquals("2h", change.newValue());
        assertEquals(java.time.Duration.ofHours(2), service.get(interval));
        assertEquals("2h", config.getString("sync.interval"));
        assertEquals(1, saves.get());
    }

    @Test
    void setRejectsAnInvalidValueWithoutWritingAnything() {
        SettingRegistry registry = new SettingRegistry();
        SettingKey<Integer> size = SettingKey.of("module", "size");
        registry.register(SettingDefinition.builder(size, SettingTypes.INTEGER, 10)
                .validator(SettingValidators.intRange(1, 20)).build());
        YamlConfiguration config = new YamlConfiguration();
        YamlSettingsService service = new YamlSettingsService(registry, () -> config, Logger.getLogger("test"));

        org.junit.jupiter.api.Assertions.assertThrows(SettingParseException.class, () -> service.set("size", "99"));
        org.junit.jupiter.api.Assertions.assertThrows(SettingParseException.class, () -> service.set("size", "abc"));
        assertEquals(10, service.get(size));
        org.junit.jupiter.api.Assertions.assertFalse(config.isSet("size"));
    }

    @Test
    void restartRequiredSettingsAreSavedButNotAppliedLive() throws SettingParseException {
        SettingRegistry registry = new SettingRegistry();
        SettingKey<Integer> port = SettingKey.of("module", "port");
        registry.register(SettingDefinition.builder(port, SettingTypes.INTEGER, 3306).requiresRestart(true).build());
        YamlConfiguration config = new YamlConfiguration();
        YamlSettingsService service = new YamlSettingsService(registry, () -> config, Logger.getLogger("test"));

        SettingChange change = service.set("port", "3307");

        org.junit.jupiter.api.Assertions.assertTrue(change.pendingRestart());
        assertEquals(3306, service.get(port));
        assertEquals(3307, config.getInt("port"));
    }

    @Test
    void resetRemovesTheKeySoTheDefaultAppliesAgain() throws SettingParseException {
        SettingRegistry registry = new SettingRegistry();
        SettingKey<java.util.List<String>> presets = SettingKey.of("module", "presets");
        registry.register(SettingDefinition.builder(presets, SettingTypes.STRING_LIST, java.util.List.of("a")).build());
        YamlConfiguration config = new YamlConfiguration();
        YamlSettingsService service = new YamlSettingsService(registry, () -> config, Logger.getLogger("test"));
        service.set("presets", "x, y ,z");
        assertEquals(java.util.List.of("x", "y", "z"), service.get(presets));

        service.reset("presets");

        assertEquals(java.util.List.of("a"), service.get(presets));
        org.junit.jupiter.api.Assertions.assertFalse(config.isSet("presets"));
    }
}

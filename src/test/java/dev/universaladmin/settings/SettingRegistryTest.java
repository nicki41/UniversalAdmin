package dev.universaladmin.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SettingRegistryTest {

    @Test
    void registersAndLooksUpByKey() {
        SettingRegistry registry = new SettingRegistry();
        SettingKey<Integer> key = SettingKey.of("core", "gui.page-size");
        SettingDefinition<Integer> definition = SettingDefinition.builder(key, SettingTypes.INTEGER, 45).build();

        registry.register(definition);

        assertEquals(definition, registry.get(key).orElseThrow());
        assertTrue(registry.all().contains(definition));
    }

    @Test
    void rejectsTwoDefinitionsForTheSameConfigPathEvenFromDifferentNamespaces() {
        SettingRegistry registry = new SettingRegistry();
        SettingKey<Integer> coreKey = SettingKey.of("core", "gui.page-size");
        SettingKey<Integer> extensionKey = SettingKey.of("some-extension", "gui.page-size");
        registry.register(SettingDefinition.builder(coreKey, SettingTypes.INTEGER, 45).build());

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> registry.register(
                SettingDefinition.builder(extensionKey, SettingTypes.INTEGER, 10).build()));

        assertTrue(exception.getMessage().contains("gui.page-size"));
    }

    @Test
    void defaultValueMustPassItsOwnValidator() {
        SettingKey<Integer> key = SettingKey.of("core", "gui.page-size");

        assertThrows(IllegalArgumentException.class, () -> SettingDefinition.builder(key, SettingTypes.INTEGER, 3)
                .validator(SettingValidators.intRange(9, 54))
                .build());
    }
}

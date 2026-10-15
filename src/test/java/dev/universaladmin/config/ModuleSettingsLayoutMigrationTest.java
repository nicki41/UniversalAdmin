package dev.universaladmin.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.logging.Logger;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class ModuleSettingsLayoutMigrationTest {

    private static final String ALPHA_CONFIG = String.join("\n",
            "config-version: 1",
            "gui:",
            "  page-size: 45",
            "  max-results: 120",
            "  search-max-results: 30",
            "vanish:",
            "  hide-join-message: false",
            "freeze:",
            "  block-commands: false",
            "reasons:",
            "  presets: [Hacking, Spam]",
            "countdown:",
            "  enabled: false",
            "  broadcast-steps: [30, 10, 5]",
            "modules:",
            "  players: true",
            "");

    @Test
    void movesModuleSettingsUnderTheirModuleAndKeepsCoreKeys() throws InvalidConfigurationException {
        YamlConfiguration config = load(ALPHA_CONFIG);

        boolean changed = ConfigMigrationRunner.withBuiltInMigrations(Logger.getLogger("test")).run(config);

        assertTrue(changed);
        assertEquals(2, config.getInt("config-version"));
        assertEquals(45, config.getInt("gui.page-size"));
        assertFalse(config.isSet("gui.max-results"));
        assertEquals(120, config.getInt("players.gui.max-results"));
        assertEquals(30, config.getInt("whitelist.gui.search-max-results"));
        assertFalse(config.getBoolean("moderation.vanish.hide-join-message", true));
        assertFalse(config.getBoolean("moderation.freeze.block-commands", true));
        assertEquals(List.of("Hacking", "Spam"), config.getStringList("moderation.reasons.presets"));
        assertFalse(config.getBoolean("server.countdown.enabled", true));
        assertEquals(List.of(30, 10, 5), config.getIntegerList("server.countdown.broadcast-steps"));
        assertFalse(config.isSet("vanish"));
        assertFalse(config.isSet("countdown"));
    }

    @Test
    void addsTheNewSectionsWithDefaults() throws InvalidConfigurationException {
        YamlConfiguration config = load(ALPHA_CONFIG);

        ConfigMigrationRunner.withBuiltInMigrations(Logger.getLogger("test")).run(config);

        assertTrue(config.getBoolean("modules.reports"));
        assertTrue(config.getBoolean("modules.chat"));
        assertTrue(config.getBoolean("commands.shortcuts.enabled"));
        assertTrue(config.getStringList("commands.shortcuts.disabled").isEmpty());
    }

    @Test
    void neverOverwritesAValueAlreadyAtTheNewPlace() throws InvalidConfigurationException {
        YamlConfiguration config = load(String.join("\n",
                "config-version: 1",
                "vanish:",
                "  hide-join-message: false",
                "moderation:",
                "  vanish:",
                "    hide-join-message: true",
                ""));

        ConfigMigrationRunner.withBuiltInMigrations(Logger.getLogger("test")).run(config);

        assertTrue(config.getBoolean("moderation.vanish.hide-join-message"));
        assertFalse(config.isSet("vanish"));
    }

    @Test
    void aCurrentConfigIsLeftAlone() throws InvalidConfigurationException {
        YamlConfiguration config = load("config-version: 2\nmodules:\n  chat: false\n");

        boolean changed = ConfigMigrationRunner.withBuiltInMigrations(Logger.getLogger("test")).run(config);

        assertFalse(changed);
        assertFalse(config.getBoolean("modules.chat"));
    }

    private static YamlConfiguration load(String yaml) throws InvalidConfigurationException {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString(yaml);
        return config;
    }
}

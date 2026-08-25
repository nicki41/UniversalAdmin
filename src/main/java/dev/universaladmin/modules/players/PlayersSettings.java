package dev.universaladmin.modules.players;

import dev.universaladmin.settings.SettingDefinition;
import dev.universaladmin.settings.SettingKey;
import dev.universaladmin.settings.SettingRegistry;
import dev.universaladmin.settings.SettingTypes;
import dev.universaladmin.settings.SettingValidators;

/**
 * Settings owned by the Players module, registered under its own {@code
 * settingsNamespace} (see {@link PlayersModule}) rather than {@code core} -
 * the first built-in module to actually use {@code SettingRegistry} for its
 * own namespace (see docs/development/settings.md).
 */
public final class PlayersSettings {

    private PlayersSettings() {
    }

    /**
     * Upper bound on how many profiles a single Offline Players/Search/
     * Recently Seen query returns - mirrors {@code AuditLogListPage}'s
     * {@code BATCH_LIMIT=200} precedent, applied at the repository query
     * layer instead of after loading everything (see {@link
     * PlayerProfileRepository#search}).
     */
    public static final SettingKey<Integer> GUI_MAX_RESULTS = SettingKey.of("players", "gui.max-results");

    public static void registerAll(SettingRegistry registry) {
        registry.register(SettingDefinition.builder(GUI_MAX_RESULTS, SettingTypes.INTEGER, 200)
                .description("Maximum profiles returned by one Offline Players/Search/Recently Seen query.")
                .validator(SettingValidators.intRange(1, 5000))
                .build());
    }
}

package dev.universaladmin.modules.whitelist;

import dev.universaladmin.settings.SettingDefinition;
import dev.universaladmin.settings.SettingKey;
import dev.universaladmin.settings.SettingRegistry;
import dev.universaladmin.settings.SettingTypes;
import dev.universaladmin.settings.SettingValidators;

/**
 * Settings owned by the Whitelist module, registered under its own {@code
 * settingsNamespace} - same precedent as {@code PlayersSettings}.
 */
public final class WhitelistSettings {

    private WhitelistSettings() {
    }

    /** Upper bound on one "Search Offline Player" query - mirrors {@code PlayersSettings.GUI_MAX_RESULTS}'s precedent. */
    public static final SettingKey<Integer> SEARCH_MAX_RESULTS = SettingKey.of("whitelist", "gui.search-max-results");

    public static void registerAll(SettingRegistry registry) {
        registry.register(SettingDefinition.builder(SEARCH_MAX_RESULTS, SettingTypes.INTEGER, 50)
                .description("Maximum offline players returned by one whitelist \"Search Offline Player\" query.")
                .validator(SettingValidators.intRange(1, 1000))
                .build());
    }
}

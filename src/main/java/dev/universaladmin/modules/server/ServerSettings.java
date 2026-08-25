package dev.universaladmin.modules.server;

import dev.universaladmin.settings.SettingDefinition;
import dev.universaladmin.settings.SettingKey;
import dev.universaladmin.settings.SettingRegistry;
import dev.universaladmin.settings.SettingTypes;
import dev.universaladmin.settings.SettingValidator;
import java.util.List;
import java.util.Optional;

/**
 * Settings owned by the Server module, registered under its own {@code
 * settingsNamespace} (see {@link ServerModule}) rather than {@code core} -
 * same precedent as {@code PlayersSettings}. Governs only the
 * shutdown/restart confirmation countdown - maintenance mode's own state is
 * not a setting, see {@link MaintenanceService}'s javadoc for why.
 */
public final class ServerSettings {

    private ServerSettings() {
    }

    /** Whether shutdown/restart broadcast a staged countdown before executing, or run immediately after confirmation. */
    public static final SettingKey<Boolean> COUNTDOWN_ENABLED = SettingKey.of("server", "countdown.enabled");

    /** Remaining-seconds marks a countdown broadcasts a warning at, largest first - e.g. {@code 60,30,10,5,4,3,2,1}. */
    public static final SettingKey<List<String>> COUNTDOWN_BROADCAST_STEPS = SettingKey.of("server", "countdown.broadcast-steps");

    public static void registerAll(SettingRegistry registry) {
        registry.register(SettingDefinition.builder(COUNTDOWN_ENABLED, SettingTypes.BOOLEAN, true)
                .description("Broadcast a staged countdown before shutdown/restart executes, instead of running immediately after confirmation.")
                .build());
        registry.register(SettingDefinition.builder(
                        COUNTDOWN_BROADCAST_STEPS, SettingTypes.STRING_LIST,
                        List.of("60", "30", "10", "5", "4", "3", "2", "1"))
                .description("Remaining-seconds marks a shutdown/restart countdown broadcasts a warning at.")
                .validator(positiveIntegers())
                .build());
    }

    private static SettingValidator<List<String>> positiveIntegers() {
        return value -> {
            for (String entry : value) {
                try {
                    if (Integer.parseInt(entry.trim()) <= 0) {
                        return Optional.of("every step must be a positive whole number of seconds (was '" + entry + "')");
                    }
                } catch (NumberFormatException e) {
                    return Optional.of("every step must be a whole number of seconds (was '" + entry + "')");
                }
            }
            return Optional.empty();
        };
    }
}

package dev.universaladmin.modules.performance;

import dev.universaladmin.settings.SettingDefinition;
import dev.universaladmin.settings.SettingKey;
import dev.universaladmin.settings.SettingRegistry;
import dev.universaladmin.settings.SettingTypes;
import dev.universaladmin.settings.SettingValidator;
import dev.universaladmin.settings.SettingValidators;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.bukkit.entity.EntityType;

/**
 * Settings owned by the Performance module, registered under its own {@code
 * performance} {@code settingsNamespace} (see {@link PerformanceModule}) -
 * same precedent as {@code ServerSettings}. {@code core.performance.refresh-interval}
 * (see {@code CoreSettings#PERFORMANCE_REFRESH_INTERVAL}) predates this
 * module's real implementation and stays where it is; everything added here
 * is new, so it follows the current rule (module settings live under the
 * module's own namespace, never {@code core}).
 */
public final class PerformanceSettings {

    private PerformanceSettings() {
    }

    /** Fires a {@code TPS below threshold} staff alert once the 1-minute average drops below this. */
    public static final SettingKey<Double> ALERT_TPS_THRESHOLD = SettingKey.of("performance", "performance.alerts.tps-threshold");

    /** Fires a {@code MSPT above threshold} staff alert once the average tick time (ms) exceeds this. */
    public static final SettingKey<Double> ALERT_MSPT_THRESHOLD_MS = SettingKey.of("performance", "performance.alerts.mspt-threshold-ms");

    /** Fires a {@code Memory above threshold} staff alert once used/max heap memory exceeds this percentage. */
    public static final SettingKey<Double> ALERT_MEMORY_THRESHOLD_PERCENT =
            SettingKey.of("performance", "performance.alerts.memory-threshold-percent");

    /** Minimum time between two alerts of the *same kind* - avoids spamming staff every refresh interval while a breach persists. */
    public static final SettingKey<Duration> ALERT_COOLDOWN = SettingKey.of("performance", "performance.alerts.cooldown");

    /** Entity Clear always refuses these types, regardless of what a caller selects - see {@link EntityClearFilter}. */
    public static final SettingKey<List<String>> ENTITY_CLEAR_PROTECTED_TYPES =
            SettingKey.of("performance", "performance.entity-clear.protected-types");

    public static void registerAll(SettingRegistry registry) {
        registry.register(SettingDefinition.builder(ALERT_TPS_THRESHOLD, SettingTypes.DOUBLE, 18.0)
                .description("Fires a staff alert once the 1-minute average TPS drops below this.")
                .validator(SettingValidators.doubleRange(0.0, 20.0))
                .build());
        registry.register(SettingDefinition.builder(ALERT_MSPT_THRESHOLD_MS, SettingTypes.DOUBLE, 50.0)
                .description("Fires a staff alert once average milliseconds-per-tick exceeds this (50ms = the full budget for 20 TPS).")
                .validator(SettingValidators.doubleRange(1.0, 1000.0))
                .build());
        registry.register(SettingDefinition.builder(ALERT_MEMORY_THRESHOLD_PERCENT, SettingTypes.DOUBLE, 90.0)
                .description("Fires a staff alert once used/max heap memory exceeds this percentage.")
                .validator(SettingValidators.doubleRange(1.0, 100.0))
                .build());
        registry.register(SettingDefinition.builder(ALERT_COOLDOWN, SettingTypes.DURATION, Duration.ofMinutes(5))
                .description("Minimum time between two alerts of the same kind, so a persisting breach doesn't spam staff every refresh interval.")
                .validator(SettingValidators.durationRange(Duration.ofSeconds(10), Duration.ofHours(1)))
                .build());
        registry.register(SettingDefinition.builder(
                        ENTITY_CLEAR_PROTECTED_TYPES, SettingTypes.STRING_LIST,
                        List.of("VILLAGER", "WANDERING_TRADER", "ARMOR_STAND", "ITEM_FRAME", "GLOW_ITEM_FRAME",
                                "PAINTING", "ENDER_DRAGON", "WITHER", "ALLAY"))
                .description("Entity types Entity Clear always refuses to remove, regardless of the filter used to invoke it. Players are always excluded and are not part of this list.")
                .validator(validEntityTypeNames())
                .build());
    }

    private static SettingValidator<List<String>> validEntityTypeNames() {
        return values -> {
            for (String value : values) {
                try {
                    EntityType.valueOf(value.trim().toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    return Optional.of("'" + value + "' is not a known entity type");
                }
            }
            return Optional.empty();
        };
    }
}

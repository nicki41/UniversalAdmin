package dev.universaladmin.modules.reports;

import dev.universaladmin.settings.SettingDefinition;
import dev.universaladmin.settings.SettingKey;
import dev.universaladmin.settings.SettingTypes;
import dev.universaladmin.settings.SettingValidators;
import java.time.Duration;
import java.util.List;

/** Settings of the Reports module, under {@code reports.*} in config.yml. */
public final class ReportsSettings {

    public static final SettingKey<Duration> COOLDOWN = SettingKey.of("reports", "reports.cooldown");
    public static final SettingKey<Integer> MAX_OPEN_PER_PLAYER = SettingKey.of("reports", "reports.max-open-per-player");
    public static final SettingKey<Integer> MIN_REASON_LENGTH = SettingKey.of("reports", "reports.min-reason-length");
    public static final SettingKey<Boolean> NOTIFY_SOUND = SettingKey.of("reports", "reports.notify-sound");
    public static final SettingKey<Boolean> REMIND_STAFF_ON_JOIN = SettingKey.of("reports", "reports.remind-staff-on-join");
    public static final SettingKey<Boolean> NOTIFY_REPORTER = SettingKey.of("reports", "reports.notify-reporter");

    private ReportsSettings() {
    }

    static List<SettingDefinition<?>> all() {
        return List.of(
                SettingDefinition.builder(COOLDOWN, SettingTypes.DURATION, Duration.ofSeconds(60))
                        .description("Minimum time between two reports by the same player (0s disables the cooldown).")
                        .validator(SettingValidators.durationRange(Duration.ZERO, Duration.ofHours(24)))
                        .build(),
                SettingDefinition.builder(MAX_OPEN_PER_PLAYER, SettingTypes.INTEGER, 3)
                        .description("How many open reports one player may have at a time (0 = unlimited).")
                        .validator(SettingValidators.intRange(0, 100))
                        .build(),
                SettingDefinition.builder(MIN_REASON_LENGTH, SettingTypes.INTEGER, 3)
                        .description("Minimum length of a report reason, so reports stay useful.")
                        .validator(SettingValidators.intRange(0, 100))
                        .build(),
                SettingDefinition.builder(NOTIFY_SOUND, SettingTypes.BOOLEAN, true)
                        .description("Play a sound for staff when a new report comes in.")
                        .build(),
                SettingDefinition.builder(REMIND_STAFF_ON_JOIN, SettingTypes.BOOLEAN, true)
                        .description("Tell staff how many reports are open when they join.")
                        .build(),
                SettingDefinition.builder(NOTIFY_REPORTER, SettingTypes.BOOLEAN, true)
                        .description("Tell the reporter (if online) when their report was resolved or rejected.")
                        .build());
    }
}

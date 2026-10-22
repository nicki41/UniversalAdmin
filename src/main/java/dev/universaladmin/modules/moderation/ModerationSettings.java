package dev.universaladmin.modules.moderation;

import dev.universaladmin.settings.SettingDefinition;
import dev.universaladmin.settings.SettingKey;
import dev.universaladmin.settings.SettingRegistry;
import dev.universaladmin.settings.SettingTypes;
import dev.universaladmin.settings.SettingValidator;
import dev.universaladmin.settings.SettingValidators;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * Settings owned by the Moderation module, registered under its own {@code
 * settingsNamespace} (see {@link ModerationModule}) rather than {@code core} -
 * same pattern {@code PlayersSettings} established. This is the
 * "configurable presets" REASONS/DURATIONS requirement: both lists are
 * ordinary settings, editable in {@code config.yml} like anything else, not
 * a bespoke config surface.
 */
public final class ModerationSettings {

    private ModerationSettings() {
    }

    public static final SettingKey<List<String>> REASON_PRESETS = SettingKey.of("moderation", "moderation.reasons.presets");
    public static final SettingKey<Boolean> NOTIFY_STAFF = SettingKey.of("moderation", "moderation.notify-staff");
    public static final SettingKey<Boolean> NOTES_JOIN_ALERT = SettingKey.of("moderation", "moderation.notes.join-alert");
    public static final SettingKey<List<String>> DURATION_PRESETS = SettingKey.of("moderation", "moderation.durations.presets");

    /** Alt detection is opt-in: nothing about addresses is stored while this is off. */
    public static final SettingKey<Boolean> ALTS_ENABLED = SettingKey.of("moderation", "moderation.alts.enabled");
    public static final SettingKey<Duration> ALTS_RETENTION = SettingKey.of("moderation", "moderation.alts.retention");
    public static final SettingKey<Boolean> ALTS_NOTIFY = SettingKey.of("moderation", "moderation.alts.notify-staff");
    public static final SettingKey<Boolean> ALTS_REFUSE_BANNED = SettingKey.of("moderation", "moderation.alts.refuse-banned-alts");

    public static final SettingKey<Boolean> VANISH_HIDE_JOIN_MESSAGE = SettingKey.of("moderation", "moderation.vanish.hide-join-message");
    public static final SettingKey<Boolean> VANISH_HIDE_QUIT_MESSAGE = SettingKey.of("moderation", "moderation.vanish.hide-quit-message");
    public static final SettingKey<Boolean> VANISH_BLOCK_ITEM_PICKUP = SettingKey.of("moderation", "moderation.vanish.block-item-pickup");
    public static final SettingKey<Boolean> VANISH_RESTORE_ON_RECONNECT = SettingKey.of("moderation", "moderation.vanish.restore-on-reconnect");
    /** Both default {@code false} - opt-in, since the spec stresses these must never be misleading; the real audit entry is always recorded regardless. */
    public static final SettingKey<Boolean> VANISH_FAKE_LEAVE_ON_VANISH = SettingKey.of("moderation", "moderation.vanish.fake-leave-on-vanish");
    public static final SettingKey<Boolean> VANISH_FAKE_JOIN_ON_UNVANISH = SettingKey.of("moderation", "moderation.vanish.fake-join-on-unvanish");

    public static final SettingKey<Boolean> FREEZE_BLOCK_MOVEMENT = SettingKey.of("moderation", "moderation.freeze.block-movement");
    public static final SettingKey<Boolean> FREEZE_BLOCK_TELEPORT = SettingKey.of("moderation", "moderation.freeze.block-teleport");
    public static final SettingKey<Boolean> FREEZE_BLOCK_INTERACTION = SettingKey.of("moderation", "moderation.freeze.block-interaction");
    public static final SettingKey<Boolean> FREEZE_BLOCK_INVENTORY = SettingKey.of("moderation", "moderation.freeze.block-inventory");
    public static final SettingKey<Boolean> FREEZE_BLOCK_COMMANDS = SettingKey.of("moderation", "moderation.freeze.block-commands");

    public static final SettingKey<Boolean> STAFFMODE_AUTO_FLY = SettingKey.of("moderation", "moderation.staffmode.auto-fly");
    public static final SettingKey<Boolean> STAFFMODE_AUTO_VANISH = SettingKey.of("moderation", "moderation.staffmode.auto-vanish");
    public static final SettingKey<Boolean> STAFFMODE_AUTO_NOCOLLISION = SettingKey.of("moderation", "moderation.staffmode.auto-nocollision");
    /**
     * How far (in blocks, straight-line, ignoring walls) a staff member can
     * aim at a player - for the "who am I looking at" indicator (held tool
     * lore + actionbar) and for the tools themselves: a right-click in the
     * air acts on that player at any distance up to this (see {@code
     * StaffModeTargeting}).
     */
    public static final SettingKey<Integer> STAFFMODE_TARGET_RANGE_BLOCKS = SettingKey.of("moderation", "moderation.staffmode.target-range-blocks");

    /**
     * A {@link DateTimeFormatter} pattern for every absolute date/time this
     * module shows (punishment created/expires/revoked times) - see
     * {@link ModerationFormat}. Both "24h vs. 12h" and "which date style" are
     * just different pattern strings, so one setting covers both instead of
     * two settings that could contradict each other: {@code "yyyy-MM-dd HH:mm"}
     * (default, 24h) or e.g. {@code "MM/dd/yyyy hh:mm a"} (12h, AM/PM).
     */
    public static final SettingKey<String> EXPIRY_DATE_PATTERN = SettingKey.of("moderation", "moderation.expiry.date-pattern");
    /** Appends the server's {@code ZoneId} (e.g. "Europe/Berlin") after the formatted date - see {@link ModerationFormat}. */
    public static final SettingKey<Boolean> EXPIRY_SHOW_TIMEZONE = SettingKey.of("moderation", "moderation.expiry.show-timezone");

    public static void registerAll(SettingRegistry registry) {
        registry.register(SettingDefinition.builder(REASON_PRESETS, SettingTypes.STRING_LIST,
                        List.of("Cheating", "Griefing", "Spam", "Advertising", "Harassment", "Bug Abuse", "Other"))
                .description("Preset reasons offered in the moderation GUI, alongside a custom free-text option.")
                .build());
        registry.register(SettingDefinition.builder(DURATION_PRESETS, SettingTypes.STRING_LIST,
                        List.of("10m", "30m", "1h", "6h", "1d", "3d", "7d", "30d", "permanent"))
                .description("Preset durations offered in the moderation GUI for temporary punishments (see DurationParser), alongside a custom option.")
                .build());

        registry.register(bool(NOTIFY_STAFF, true, "Tell online staff (universaladmin.moderation.notify) whenever someone is punished or pardoned."));
        registry.register(bool(NOTES_JOIN_ALERT, true, "Tell staff with universaladmin.moderation.notes when a player who has staff notes joins."));
        registry.register(bool(ALTS_ENABLED, false,
                "Alt detection: remember a keyed hash (never the address itself) of every login address to find accounts that share one."));
        registry.register(SettingDefinition.builder(ALTS_RETENTION, SettingTypes.DURATION, Duration.ofDays(90))
                .description("How long a login address is remembered for alt detection; older ones are deleted, also while it is off.")
                .validator(value -> value.compareTo(Duration.ofDays(1)) < 0
                        ? Optional.of("must be at least 1d") : Optional.empty())
                .build());
        registry.register(bool(ALTS_NOTIFY, true,
                "Tell staff with universaladmin.moderation.alts when a player joins who shares an address with a banned account."));
        registry.register(bool(ALTS_REFUSE_BANNED, false,
                "Refuse players who share an address with a banned account (universaladmin.bypass.alt-check lets them in)."));

        registry.register(bool(VANISH_HIDE_JOIN_MESSAGE, true, "Suppress the real join message when a persistently-vanished player reconnects."));
        registry.register(bool(VANISH_HIDE_QUIT_MESSAGE, true, "Suppress the real quit message when a vanished player disconnects."));
        registry.register(bool(VANISH_BLOCK_ITEM_PICKUP, true, "Prevent a vanished player from picking up items."));
        registry.register(bool(VANISH_RESTORE_ON_RECONNECT, true, "Re-apply vanish automatically if a player reconnects while persistently vanished."));
        registry.register(bool(VANISH_FAKE_LEAVE_ON_VANISH, false, "Broadcast a fake leave message (to non-bypass players) when a player vanishes."));
        registry.register(bool(VANISH_FAKE_JOIN_ON_UNVANISH, false, "Broadcast a fake join message (to non-bypass players) when a player unvanishes."));

        registry.register(bool(FREEZE_BLOCK_MOVEMENT, true, "Block movement for a frozen player."));
        registry.register(bool(FREEZE_BLOCK_TELEPORT, true, "Block teleportation for a frozen player."));
        registry.register(bool(FREEZE_BLOCK_INTERACTION, true, "Block world interaction for a frozen player."));
        registry.register(bool(FREEZE_BLOCK_INVENTORY, true, "Block inventory clicks for a frozen player."));
        registry.register(bool(FREEZE_BLOCK_COMMANDS, true, "Block command usage for a frozen player."));

        registry.register(bool(STAFFMODE_AUTO_FLY, true, "Grant flight automatically when entering staff mode."));
        registry.register(bool(STAFFMODE_AUTO_VANISH, true, "Vanish automatically when entering staff mode."));
        registry.register(bool(STAFFMODE_AUTO_NOCOLLISION, true, "Disable collision automatically when entering staff mode."));
        registry.register(SettingDefinition.builder(STAFFMODE_TARGET_RANGE_BLOCKS, SettingTypes.INTEGER, 128)
                .description("How far (blocks, ignores walls) Staff Mode tools reach: right-click in the air acts on the player in your crosshair up to this distance.")
                .validator(SettingValidators.intRange(1, 1000))
                .build());

        registry.register(SettingDefinition.builder(EXPIRY_DATE_PATTERN, SettingTypes.STRING, "yyyy-MM-dd HH:mm")
                .description("DateTimeFormatter pattern for punishment created/expires/revoked timestamps. "
                        + "24h example: 'yyyy-MM-dd HH:mm'. 12h example: 'MM/dd/yyyy hh:mm a'.")
                .validator(validDateTimeFormatterPattern())
                .build());
        registry.register(bool(EXPIRY_SHOW_TIMEZONE, true, "Append the server's time zone (e.g. Europe/Berlin) after every formatted date/time."));
    }

    private static SettingDefinition<Boolean> bool(SettingKey<Boolean> key, boolean defaultValue, String description) {
        return SettingDefinition.builder(key, SettingTypes.BOOLEAN, defaultValue).description(description).build();
    }

    private static SettingValidator<String> validDateTimeFormatterPattern() {
        return value -> {
            try {
                DateTimeFormatter.ofPattern(value);
                return Optional.empty();
            } catch (IllegalArgumentException e) {
                return Optional.of("'" + value + "' is not a valid DateTimeFormatter pattern: " + e.getMessage());
            }
        };
    }
}

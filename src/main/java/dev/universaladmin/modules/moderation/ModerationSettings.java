package dev.universaladmin.modules.moderation;

import dev.universaladmin.settings.SettingDefinition;
import dev.universaladmin.settings.SettingKey;
import dev.universaladmin.settings.SettingRegistry;
import dev.universaladmin.settings.SettingTypes;
import dev.universaladmin.settings.SettingValidator;
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

    public static final SettingKey<List<String>> REASON_PRESETS = SettingKey.of("moderation", "reasons.presets");
    public static final SettingKey<List<String>> DURATION_PRESETS = SettingKey.of("moderation", "durations.presets");

    public static final SettingKey<Boolean> VANISH_HIDE_JOIN_MESSAGE = SettingKey.of("moderation", "vanish.hide-join-message");
    public static final SettingKey<Boolean> VANISH_HIDE_QUIT_MESSAGE = SettingKey.of("moderation", "vanish.hide-quit-message");
    public static final SettingKey<Boolean> VANISH_BLOCK_ITEM_PICKUP = SettingKey.of("moderation", "vanish.block-item-pickup");
    public static final SettingKey<Boolean> VANISH_RESTORE_ON_RECONNECT = SettingKey.of("moderation", "vanish.restore-on-reconnect");
    /** Both default {@code false} - opt-in, since the spec stresses these must never be misleading; the real audit entry is always recorded regardless. */
    public static final SettingKey<Boolean> VANISH_FAKE_LEAVE_ON_VANISH = SettingKey.of("moderation", "vanish.fake-leave-on-vanish");
    public static final SettingKey<Boolean> VANISH_FAKE_JOIN_ON_UNVANISH = SettingKey.of("moderation", "vanish.fake-join-on-unvanish");

    public static final SettingKey<Boolean> FREEZE_BLOCK_MOVEMENT = SettingKey.of("moderation", "freeze.block-movement");
    public static final SettingKey<Boolean> FREEZE_BLOCK_TELEPORT = SettingKey.of("moderation", "freeze.block-teleport");
    public static final SettingKey<Boolean> FREEZE_BLOCK_INTERACTION = SettingKey.of("moderation", "freeze.block-interaction");
    public static final SettingKey<Boolean> FREEZE_BLOCK_INVENTORY = SettingKey.of("moderation", "freeze.block-inventory");
    public static final SettingKey<Boolean> FREEZE_BLOCK_COMMANDS = SettingKey.of("moderation", "freeze.block-commands");

    public static final SettingKey<Boolean> STAFFMODE_AUTO_FLY = SettingKey.of("moderation", "staffmode.auto-fly");
    public static final SettingKey<Boolean> STAFFMODE_AUTO_VANISH = SettingKey.of("moderation", "staffmode.auto-vanish");
    public static final SettingKey<Boolean> STAFFMODE_AUTO_NOCOLLISION = SettingKey.of("moderation", "staffmode.auto-nocollision");

    /**
     * A {@link DateTimeFormatter} pattern for every absolute date/time this
     * module shows (punishment created/expires/revoked times) - see
     * {@link ModerationFormat}. Both "24h vs. 12h" and "which date style" are
     * just different pattern strings, so one setting covers both instead of
     * two settings that could contradict each other: {@code "yyyy-MM-dd HH:mm"}
     * (default, 24h) or e.g. {@code "MM/dd/yyyy hh:mm a"} (12h, AM/PM).
     */
    public static final SettingKey<String> EXPIRY_DATE_PATTERN = SettingKey.of("moderation", "expiry.date-pattern");
    /** Appends the server's {@code ZoneId} (e.g. "Europe/Berlin") after the formatted date - see {@link ModerationFormat}. */
    public static final SettingKey<Boolean> EXPIRY_SHOW_TIMEZONE = SettingKey.of("moderation", "expiry.show-timezone");

    public static void registerAll(SettingRegistry registry) {
        registry.register(SettingDefinition.builder(REASON_PRESETS, SettingTypes.STRING_LIST,
                        List.of("Cheating", "Griefing", "Spam", "Advertising", "Harassment", "Bug Abuse", "Other"))
                .description("Preset reasons offered in the moderation GUI, alongside a custom free-text option.")
                .build());
        registry.register(SettingDefinition.builder(DURATION_PRESETS, SettingTypes.STRING_LIST,
                        List.of("10m", "30m", "1h", "6h", "1d", "3d", "7d", "30d", "permanent"))
                .description("Preset durations offered in the moderation GUI for temporary punishments (see DurationParser), alongside a custom option.")
                .build());

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

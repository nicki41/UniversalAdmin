package dev.universaladmin.modules.players.action;

import dev.universaladmin.core.time.DurationParser;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.localization.MessageService;
import java.time.Duration;
import java.util.Locale;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.potion.PotionEffectType;

/**
 * Formatting for the Players actions' success messages. Numbers go into the
 * message as text, so a coordinate never shows up as "-1,234"; names the
 * client already knows (effects) are passed as translatable markup, so every
 * player reads them in their own game language.
 */
final class PlayerResults {

    private static final long TICKS_PER_SECOND = 20L;

    private PlayerResults() {
    }

    /** The coloured ON/OFF label. */
    static MessageKey status(boolean on) {
        return MessageKey.of(on ? "players.gui.status.on" : "players.gui.status.off");
    }

    static MessageKey gamemode(GameMode mode) {
        return MessageKey.of("players.gamemode." + mode.name().toLowerCase(Locale.ROOT));
    }

    /** The effect's name as the client shows it, e.g. "Speed" / "Schnelligkeit". */
    static Object effect(PotionEffectType type) {
        return MessageService.markup("<lang:" + type.translationKey() + ">");
    }

    /** "12", "12.5" - no grouping, at most one decimal. */
    static String number(double value) {
        return value == Math.rint(value) ? Long.toString((long) value) : String.format(Locale.ROOT, "%.1f", value);
    }

    /** "world 120 64 -30". */
    static String location(Location location) {
        return (location.getWorld() == null ? "?" : location.getWorld().getName())
                + " " + location.getBlockX() + " " + location.getBlockY() + " " + location.getBlockZ();
    }

    /** "30s", "5m", or "infinite" for an effect without end. */
    static Object duration(int ticks) {
        if (ticks < 0 || ticks == Integer.MAX_VALUE) {
            return MessageKey.of("players.result.infinite");
        }
        return DurationParser.format(Duration.ofSeconds(Math.max(1, ticks / TICKS_PER_SECOND)));
    }
}

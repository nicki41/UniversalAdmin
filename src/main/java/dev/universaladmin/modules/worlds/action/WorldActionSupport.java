package dev.universaladmin.modules.worlds.action;

import dev.universaladmin.action.ActionResult;
import dev.universaladmin.localization.MessageKey;
import java.util.Locale;
import org.bukkit.Location;

/**
 * Shared bits of the world actions: the "world not found" failure, and the
 * number formatting their success messages use. Numbers are passed to the
 * message as text so a coordinate never shows up as "-1,234".
 */
final class WorldActionSupport {

    private static final long TICKS_PER_DAY = 24000L;

    private WorldActionSupport() {
    }

    static <R> ActionResult<R> worldNotFound() {
        return ActionResult.failure(ActionResult.FailureReason.NOT_FOUND, MessageKey.of("worlds.action.world-not-found"));
    }

    /** "12", "12.5" - no grouping, at most one decimal. */
    static String number(double value) {
        return value == Math.rint(value) ? Long.toString((long) value) : String.format(Locale.ROOT, "%.1f", value);
    }

    /** Block coordinates, "120 64 -30". */
    static String blockCoordinates(Location location) {
        return location.getBlockX() + " " + location.getBlockY() + " " + location.getBlockZ();
    }

    /** In-game clock time for a day time in ticks - tick 0 is 06:00, 6000 is noon, 18000 midnight. */
    static String clock(long ticks) {
        long dayTicks = Math.floorMod(ticks, TICKS_PER_DAY);
        long hours = (dayTicks / 1000 + 6) % 24;
        long minutes = (dayTicks % 1000) * 60 / 1000;
        return String.format(Locale.ROOT, "%02d:%02d", hours, minutes);
    }
}

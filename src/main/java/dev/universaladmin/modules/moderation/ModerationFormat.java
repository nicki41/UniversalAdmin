package dev.universaladmin.modules.moderation;

import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.localization.MessageService;
import dev.universaladmin.settings.SettingsService;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Pure presentation formatting shared by the enforcement messages and the
 * GUI - no logic beyond turning an {@link Instant} into text. Date/time
 * style (24h vs. 12h, which date order) and whether to show the server's
 * time zone are both configurable via {@link ModerationSettings#EXPIRY_DATE_PATTERN}/
 * {@link ModerationSettings#EXPIRY_SHOW_TIMEZONE} rather than hardcoded, and
 * re-read on every call (not cached) so a changed setting takes effect
 * immediately after {@code /admin reload}, same as every other setting.
 */
public final class ModerationFormat {

    private ModerationFormat() {
    }

    /** A single absolute timestamp (e.g. "created at"), or {@code "-"} for {@code null}. */
    public static String instant(Instant instant, SettingsService settings) {
        if (instant == null) {
            return "-";
        }
        ZoneId zone = ZoneId.systemDefault();
        DateTimeFormatter formatter = formatterFor(settings, zone);
        String formatted = formatter.format(instant);
        return settings.get(ModerationSettings.EXPIRY_SHOW_TIMEZONE) ? formatted + " (" + zone + ")" : formatted;
    }

    /**
     * An expiry timestamp: the localized "Permanent" text for {@code null},
     * otherwise the absolute date/time (see {@link #instant}) followed by a
     * remaining-time breakdown in parentheses, e.g.
     * {@code "2026-09-15 14:30 (Europe/Berlin) (in 3mo 2d 5h 12m)"}.
     */
    public static String expiry(Instant expiresAt, SettingsService settings, MessageService messages) {
        if (expiresAt == null) {
            return messages.get(MessageKey.of("moderation.gui.permanent"));
        }
        String absolute = instant(expiresAt, settings);
        String relative = remaining(expiresAt, messages);
        return absolute + " (" + relative + ")";
    }

    /**
     * "in 3mo 2d 5h 12m" / "expired" style countdown, cascading from months
     * down to minutes - each unit's remainder carries into the next, so this
     * is an accurate calendar breakdown (via {@link ChronoUnit} on {@link
     * ZonedDateTime}), not a naive division of total seconds (which would
     * get "months" wrong since months aren't a fixed length). Zero-value
     * units are omitted entirely, except minutes, which always shows (even
     * "0m") so the string is never empty for an imminent expiry.
     */
    public static String remaining(Instant expiresAt, MessageService messages) {
        ZoneId zone = ZoneId.systemDefault();
        ZonedDateTime now = ZonedDateTime.now(zone);
        ZonedDateTime end = expiresAt.atZone(zone);
        if (!end.isAfter(now)) {
            return messages.get(MessageKey.of("moderation.gui.expired"));
        }

        long months = ChronoUnit.MONTHS.between(now, end);
        ZonedDateTime cursor = now.plusMonths(months);
        long weeks = ChronoUnit.WEEKS.between(cursor, end);
        cursor = cursor.plusWeeks(weeks);
        long days = ChronoUnit.DAYS.between(cursor, end);
        cursor = cursor.plusDays(days);
        long hours = ChronoUnit.HOURS.between(cursor, end);
        cursor = cursor.plusHours(hours);
        long minutes = ChronoUnit.MINUTES.between(cursor, end);

        List<String> parts = new ArrayList<>();
        addIfPositive(parts, months, "moderation.gui.time-unit.months", messages);
        addIfPositive(parts, weeks, "moderation.gui.time-unit.weeks", messages);
        addIfPositive(parts, days, "moderation.gui.time-unit.days", messages);
        addIfPositive(parts, hours, "moderation.gui.time-unit.hours", messages);
        parts.add(messages.get(MessageKey.of("moderation.gui.time-unit.minutes"), minutes));

        return messages.get(MessageKey.of("moderation.gui.time-remaining"), String.join(" ", parts));
    }

    private static void addIfPositive(List<String> parts, long amount, String unitKey, MessageService messages) {
        if (amount > 0) {
            parts.add(messages.get(MessageKey.of(unitKey), amount));
        }
    }

    private static DateTimeFormatter formatterFor(SettingsService settings, ZoneId zone) {
        String pattern = settings.get(ModerationSettings.EXPIRY_DATE_PATTERN);
        try {
            return DateTimeFormatter.ofPattern(pattern, Locale.ROOT).withZone(zone);
        } catch (IllegalArgumentException e) {
            // ModerationSettings' own validator rejects a bad pattern before
            // it can ever reach here - this is only reachable if config.yml
            // was hand-edited between validation and this read, same
            // "never crash on a bad value" fallback every setting gets.
            return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT).withZone(zone);
        }
    }
}

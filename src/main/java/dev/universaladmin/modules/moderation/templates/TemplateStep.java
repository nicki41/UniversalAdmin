package dev.universaladmin.modules.moderation.templates;

import dev.universaladmin.core.time.DurationParseException;
import dev.universaladmin.core.time.DurationParser;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

/**
 * One step of a {@link PunishmentTemplate}, written in
 * {@code punishment-templates.yml} as {@code warn}, {@code kick},
 * {@code mute [duration]} or {@code ban [duration]} - without a duration,
 * mute and ban are permanent.
 *
 * @param duration {@code null} for a permanent mute/ban, and always for warn and kick
 */
public record TemplateStep(Kind kind, Duration duration) {

    /** What a step does - each maps to the moderation action that carries it out. */
    public enum Kind {
        WARN,
        KICK,
        MUTE,
        BAN
    }

    public TemplateStep {
        if ((kind == Kind.WARN || kind == Kind.KICK) && duration != null) {
            throw new IllegalArgumentException(kind + " takes no duration");
        }
    }

    /**
     * Parses one step.
     *
     * @throws IllegalArgumentException with a readable message if {@code raw} is not a step
     */
    public static TemplateStep parse(String raw) {
        String[] parts = raw == null ? new String[0] : raw.trim().split("\\s+", 2);
        if (parts.length == 0 || parts[0].isEmpty()) {
            throw new IllegalArgumentException("empty step");
        }
        Kind kind;
        try {
            kind = Kind.valueOf(parts[0].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("unknown punishment '" + parts[0] + "' (warn, kick, mute or ban)");
        }
        if (parts.length == 1) {
            return new TemplateStep(kind, null);
        }
        if (kind == Kind.WARN || kind == Kind.KICK) {
            throw new IllegalArgumentException(parts[0] + " takes no duration");
        }
        try {
            Optional<Duration> duration = DurationParser.parse(parts[1]);
            return new TemplateStep(kind, duration.orElse(null));
        } catch (DurationParseException e) {
            throw new IllegalArgumentException("invalid duration '" + parts[1] + "'");
        }
    }

    public boolean permanent() {
        return duration == null;
    }

    /** The step as it is written in the file ({@code mute 1h}). */
    @Override
    public String toString() {
        return kind.name().toLowerCase(Locale.ROOT) + (duration == null ? "" : " " + DurationParser.format(duration));
    }
}

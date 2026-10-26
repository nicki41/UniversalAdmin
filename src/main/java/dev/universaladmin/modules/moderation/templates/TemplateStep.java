package dev.universaladmin.modules.moderation.templates;

import dev.universaladmin.core.time.DurationParseException;
import dev.universaladmin.core.time.DurationParser;
import dev.universaladmin.status.MuteKind;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

/**
 * One step of a {@link PunishmentTemplate}, written in
 * {@code punishment-templates.yml} as {@code warn}, {@code kick},
 * {@code mute [duration]}, {@code chatmute [duration]},
 * {@code voicemute [duration]} or {@code ban [duration]} - without a
 * duration, mutes and bans are permanent.
 *
 * @param duration {@code null} for a permanent mute/ban, and always for warn and kick
 * @param muteKind where a mute step silences the player ({@link MuteKind#FULL} for {@code mute} and every other step)
 */
public record TemplateStep(Kind kind, Duration duration, MuteKind muteKind) {

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
        muteKind = kind != Kind.MUTE || muteKind == null ? MuteKind.FULL : muteKind;
    }

    public TemplateStep(Kind kind, Duration duration) {
        this(kind, duration, MuteKind.FULL);
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
        MuteKind muteKind = MuteKind.FULL;
        String name = parts[0].toLowerCase(Locale.ROOT);
        if (name.equals("chatmute") || name.equals("voicemute")) {
            kind = Kind.MUTE;
            muteKind = name.equals("chatmute") ? MuteKind.CHAT : MuteKind.VOICE;
        } else {
            try {
                kind = Kind.valueOf(name.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("unknown punishment '" + parts[0] + "' (warn, kick, mute, chatmute, voicemute or ban)");
            }
        }
        if (parts.length == 1) {
            return new TemplateStep(kind, null, muteKind);
        }
        if (kind == Kind.WARN || kind == Kind.KICK) {
            throw new IllegalArgumentException(parts[0] + " takes no duration");
        }
        try {
            Optional<Duration> duration = DurationParser.parse(parts[1]);
            return new TemplateStep(kind, duration.orElse(null), muteKind);
        } catch (DurationParseException e) {
            throw new IllegalArgumentException("invalid duration '" + parts[1] + "'");
        }
    }

    public boolean permanent() {
        return duration == null;
    }

    /** The step as it is written in the file ({@code mute 1h}, {@code voicemute 1h}). */
    @Override
    public String toString() {
        String name = switch (muteKind) {
            case CHAT -> "chatmute";
            case VOICE -> "voicemute";
            case FULL -> kind.name().toLowerCase(Locale.ROOT);
        };
        return name + (duration == null ? "" : " " + DurationParser.format(duration));
    }
}

package dev.universaladmin.core.time;

import java.time.Duration;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the human duration syntax used everywhere a staff member types a
 * duration (GUI prompts, {@code /admin tempban}, whitelist expiry, chat slow
 * mode, ...): {@code 10m}, {@code 1h}, {@code 3d}, {@code 2w}, compound forms
 * like {@code 1d12h}, and {@code permanent}/{@code perm} for "no expiry".
 * Pure and side-effect-free, so it's testable without a server - see
 * docs/development/testing.md. {@link #format(Duration)} is the inverse.
 */
public final class DurationParser {

    private static final Pattern TOKEN = Pattern.compile("(?i)(\\d+)(w|d|h|m|s)");

    private DurationParser() {
    }

    /**
     * @return empty for "permanent"/"perm", otherwise the parsed duration
     * @throws DurationParseException if {@code raw} isn't a recognized duration
     */
    public static Optional<Duration> parse(String raw) {
        if (raw == null) {
            throw new DurationParseException("Duration must not be null");
        }
        String compact = raw.trim().replaceAll("\\s+", "");
        if (compact.isEmpty()) {
            throw new DurationParseException("Duration must not be blank");
        }
        if (compact.equalsIgnoreCase("permanent") || compact.equalsIgnoreCase("perm")) {
            return Optional.empty();
        }

        Matcher matcher = TOKEN.matcher(compact);
        long totalSeconds = 0;
        int matchedUpTo = 0;
        boolean matchedAnyToken = false;
        while (matcher.find()) {
            if (matcher.start() != matchedUpTo) {
                throw new DurationParseException("Invalid duration: '" + raw + "'");
            }
            long amount = Long.parseLong(matcher.group(1));
            totalSeconds += amount * secondsPerUnit(matcher.group(2).charAt(0));
            matchedUpTo = matcher.end();
            matchedAnyToken = true;
        }
        if (!matchedAnyToken || matchedUpTo != compact.length()) {
            throw new DurationParseException("Invalid duration: '" + raw + "'");
        }
        if (totalSeconds <= 0) {
            throw new DurationParseException("Duration must be positive: '" + raw + "'");
        }
        return Optional.of(Duration.ofSeconds(totalSeconds));
    }

    private static long secondsPerUnit(char unit) {
        return switch (Character.toLowerCase(unit)) {
            case 's' -> 1L;
            case 'm' -> 60L;
            case 'h' -> 3600L;
            case 'd' -> 86400L;
            case 'w' -> 604800L;
            default -> throw new DurationParseException("Unknown duration unit '" + unit + "'");
        };
    }

    /**
     * Formats {@code duration} in the same compact syntax {@link #parse}
     * accepts, largest units first, e.g. {@code 1d 12h} or {@code 45s}.
     * Sub-second remainders are dropped; anything under a second is {@code 0s}.
     */
    public static String format(Duration duration) {
        long seconds = Math.max(0, duration.toSeconds());
        if (seconds == 0) {
            return "0s";
        }
        StringBuilder out = new StringBuilder();
        seconds = append(out, seconds, 604800L, 'w');
        seconds = append(out, seconds, 86400L, 'd');
        seconds = append(out, seconds, 3600L, 'h');
        seconds = append(out, seconds, 60L, 'm');
        append(out, seconds, 1L, 's');
        return out.toString();
    }

    private static long append(StringBuilder out, long seconds, long unitSeconds, char unit) {
        long amount = seconds / unitSeconds;
        if (amount > 0) {
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(amount).append(unit);
        }
        return seconds % unitSeconds;
    }
}

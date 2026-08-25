package dev.universaladmin.modules.moderation;

import java.time.Duration;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the duration presets/custom input the moderation GUI accepts:
 * {@code 10m}, {@code 1h}, {@code 3d}, compound forms like {@code 1d12h},
 * and {@code permanent}/{@code perm} for "no expiry". Pure and
 * side-effect-free, so it's testable without a server - see
 * docs/development/testing.md.
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
}

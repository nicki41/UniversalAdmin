package dev.universaladmin.modules.moderation.importing;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

/**
 * Turning what other plugins store about a target into a UUID and a name.
 *
 * <p>An IP ban without a known player (a {@code banned-ips.json} entry, a
 * LiteBans IP ban with {@code #offline#}) still needs a {@code target_id}:
 * it gets a UUID derived from the address, so importing the same address
 * twice gives the same id. Enforcement only looks at {@code target_ip}, so
 * the id is never mistaken for a real player.
 */
public final class ImportTargets {

    /** Stored when a source has no name for a player at all. */
    public static final String UNKNOWN_NAME = "unknown";

    private ImportTargets() {
    }

    /** Parses a UUID with or without dashes (AdvancedBan stores it without); empty for anything else. */
    public static Optional<UUID> uuid(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String value = raw.trim();
        if (value.length() == 32 && value.matches("[0-9a-fA-F]{32}")) {
            value = value.substring(0, 8) + "-" + value.substring(8, 12) + "-" + value.substring(12, 16) + "-"
                    + value.substring(16, 20) + "-" + value.substring(20);
        }
        if (value.length() != 36) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** The stable stand-in id for an IP ban without a player - see the class documentation. */
    public static UUID forIp(String ip) {
        return UUID.nameUUIDFromBytes(("universaladmin:ip:" + ip.trim()).getBytes(StandardCharsets.UTF_8));
    }

    /** The name shown for an IP ban without a player: the address if it fits the 16-character column. */
    public static String nameForIp(String ip) {
        String value = ip.trim();
        return value.length() <= ImportedPunishment.MAX_NAME ? value : "IP address";
    }

    /** Whether {@code raw} looks like an IPv4 or IPv6 address (AdvancedBan keeps IP bans in its uuid column). */
    public static boolean looksLikeIp(String raw) {
        if (raw == null) {
            return false;
        }
        String value = raw.trim();
        return value.matches("\\d{1,3}(\\.\\d{1,3}){3}") || (value.contains(":") && value.matches("[0-9a-fA-F:.]+"));
    }
}

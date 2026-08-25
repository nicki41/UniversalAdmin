package dev.universaladmin.modules.whitelist;

import java.time.Instant;
import java.util.UUID;

/**
 * UniversalAdmin's own metadata for one whitelisted player - always paired
 * with (but never the source of truth for) Bukkit's native whitelist entry;
 * see {@link WhitelistService}'s javadoc for how the two stay in sync.
 *
 * @param addedById   nullable - {@code null} for a console/system actor
 * @param reason      nullable, admin-facing
 * @param notes       nullable, admin-facing
 * @param expiresAt   nullable - {@code null} means permanent
 */
public record WhitelistEntry(
        UUID playerId,
        String playerName,
        WhitelistSource source,
        UUID addedById,
        String addedByName,
        Instant addedAt,
        String reason,
        String notes,
        Instant expiresAt) {

    public boolean isExpired(Instant now) {
        return expiresAt != null && expiresAt.isBefore(now);
    }
}

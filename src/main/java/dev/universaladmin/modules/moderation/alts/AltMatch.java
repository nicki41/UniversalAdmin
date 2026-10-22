package dev.universaladmin.modules.moderation.alts;

import java.time.Instant;
import java.util.UUID;

/**
 * Another account that connected from at least one of the same addresses.
 *
 * @param sharedAddresses how many of the player's addresses this account used too
 * @param lastSeen        the last time this account connected from one of them
 * @param banned          whether this account is banned right now (filled in by {@link AltService})
 */
public record AltMatch(UUID playerId, String name, int sharedAddresses, Instant lastSeen, boolean banned) {

    public AltMatch withBanned(boolean banned) {
        return new AltMatch(playerId, name, sharedAddresses, lastSeen, banned);
    }
}

package dev.universaladmin.modules.players;

import java.time.Instant;
import java.util.UUID;

/** Immutable record of what UniversalAdmin knows about a player. */
public record PlayerProfile(UUID id, String lastKnownName, Instant firstJoin, Instant lastSeen) {

    public PlayerProfile withLastSeen(String name, Instant seenAt) {
        return new PlayerProfile(id, name, firstJoin, seenAt);
    }
}

package dev.universaladmin.modules.moderation;

import java.time.Instant;
import java.util.UUID;

/** Persisted "this player was vanished" marker - existence is the state, see {@link VanishRepository}. */
public record VanishRecord(UUID playerId, Instant vanishedAt) {

    public static VanishRecord now(UUID playerId) {
        return new VanishRecord(playerId, Instant.now());
    }
}

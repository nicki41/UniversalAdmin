package dev.universaladmin.modules.moderation;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory "who is frozen right now" - {@link FreezeGuardListener} reads
 * this on every move/teleport/interact/inventory/command event, never the
 * database (see {@link VanishRuntimeState} for the identical reasoning).
 * Populated by {@code FreezeAction}/{@code UnfreezeAction} on toggle and by
 * one async lookup on join. Who froze the player and since when is kept
 * for {@link FreezeDisplay}'s marker.
 */
public final class FreezeRuntimeState {

    /** Who froze a player and when; {@code by} is empty when that is not known. */
    public record Freeze(String by, Instant since) {
    }

    private final Map<UUID, Freeze> frozen = new ConcurrentHashMap<>();

    public boolean isFrozen(UUID playerId) {
        return frozen.containsKey(playerId);
    }

    public Optional<Freeze> freeze(UUID playerId) {
        return Optional.ofNullable(frozen.get(playerId));
    }

    public void freeze(UUID playerId, String by, Instant since) {
        frozen.put(playerId, new Freeze(by == null ? "" : by, since == null ? Instant.now() : since));
    }

    public void setFrozen(UUID playerId, boolean value) {
        if (value) {
            frozen.putIfAbsent(playerId, new Freeze("", Instant.now()));
        } else {
            frozen.remove(playerId);
        }
    }
}

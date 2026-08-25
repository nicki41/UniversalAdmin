package dev.universaladmin.modules.moderation;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory "who is frozen right now" - {@link FreezeGuardListener} reads
 * this on every move/teleport/interact/inventory/command event, never the
 * database (see {@link VanishRuntimeState} for the identical reasoning).
 * Populated by {@code FreezeAction}/{@code UnfreezeAction} on toggle and by
 * one async lookup on join.
 */
public final class FreezeRuntimeState {

    private final Set<UUID> frozen = ConcurrentHashMap.newKeySet();

    public boolean isFrozen(UUID playerId) {
        return frozen.contains(playerId);
    }

    public void setFrozen(UUID playerId, boolean value) {
        if (value) {
            frozen.add(playerId);
        } else {
            frozen.remove(playerId);
        }
    }
}

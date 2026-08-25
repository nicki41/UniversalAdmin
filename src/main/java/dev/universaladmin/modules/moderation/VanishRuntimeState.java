package dev.universaladmin.modules.moderation;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory "who is vanished right now" - the only thing every hot-path
 * listener (mob targeting, item pickup, collision) is allowed to read.
 * Deliberately not a {@code Repository} - exactly the kind of ephemeral,
 * process-lifetime state {@code PlayerSessionTracker} already established
 * the pattern for; {@link VanishRepository} is the separate, genuinely
 * persistent half (reconnect-restore), written to far less often than this
 * is read.
 */
public final class VanishRuntimeState {

    private final Set<UUID> vanished = ConcurrentHashMap.newKeySet();

    public boolean isVanished(UUID playerId) {
        return vanished.contains(playerId);
    }

    public void setVanished(UUID playerId, boolean value) {
        if (value) {
            vanished.add(playerId);
        } else {
            vanished.remove(playerId);
        }
    }

    public Set<UUID> all() {
        return Set.copyOf(vanished);
    }
}

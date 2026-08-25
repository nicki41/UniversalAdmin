package dev.universaladmin.modules.moderation;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory "who currently has godmode on" - not persisted (nothing
 * requires it to survive a restart, unlike Vanish's explicit
 * reconnect-restore requirement), purely for GUI status display and
 * {@code GodmodeService}'s own toggle bookkeeping. The actual damage
 * blocking is {@link org.bukkit.entity.Entity#setInvulnerable(boolean)}
 * itself, not anything this class enforces.
 */
public final class GodmodeState {

    private final Set<UUID> enabled = ConcurrentHashMap.newKeySet();

    public boolean isEnabled(UUID playerId) {
        return enabled.contains(playerId);
    }

    public void setEnabled(UUID playerId, boolean value) {
        if (value) {
            enabled.add(playerId);
        } else {
            enabled.remove(playerId);
        }
    }
}

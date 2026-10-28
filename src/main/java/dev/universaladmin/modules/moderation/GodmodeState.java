package dev.universaladmin.modules.moderation;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory "who online has godmode on" - for GUI status display and
 * {@code PlayerStatus}, which answer from any thread. The truth is the
 * player's invulnerability ({@link org.bukkit.entity.Entity#setInvulnerable(boolean)}),
 * which Minecraft saves with the player's data: {@link GodmodeSyncListener}
 * reads it back on join, so godmode left on before a restart shows as on.
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

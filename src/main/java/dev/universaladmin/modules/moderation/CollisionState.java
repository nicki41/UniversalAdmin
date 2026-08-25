package dev.universaladmin.modules.moderation;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Player;

/**
 * Composes the three independent reasons a player might have collision
 * disabled - a manual toggle (this class' own tracked set), being
 * vanished, or an active staff-mode session with {@code
 * staffmode.auto-nocollision} on - so turning any one of them off doesn't
 * clobber the others. {@link #refresh} is the single place {@link
 * org.bukkit.entity.LivingEntity#setCollidable(boolean)} gets called from;
 * every caller (the manual toggle action, {@code VanishService}, {@code
 * StaffModeService}) goes through it instead of calling {@code
 * setCollidable} directly.
 */
public final class CollisionState {

    private final Set<UUID> manuallyDisabled = ConcurrentHashMap.newKeySet();

    public boolean isManuallyDisabled(UUID playerId) {
        return manuallyDisabled.contains(playerId);
    }

    public void setManuallyDisabled(UUID playerId, boolean value) {
        if (value) {
            manuallyDisabled.add(playerId);
        } else {
            manuallyDisabled.remove(playerId);
        }
    }

    /** Recomputes and applies the effective collidable state for {@code player}. Must run on the main thread. */
    public void refresh(Player player, boolean vanished, boolean staffModeAutoNoCollision) {
        boolean effective = vanished || staffModeAutoNoCollision || isManuallyDisabled(player.getUniqueId());
        player.setCollidable(!effective);
    }
}

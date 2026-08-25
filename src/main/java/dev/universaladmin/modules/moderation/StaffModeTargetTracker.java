package dev.universaladmin.modules.moderation;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

/**
 * Runs periodically (see {@code ModerationModule#onEnable}'s repeating
 * task) for every online, Staff-Mode-active player: refreshes whichever
 * status-aware tool is currently held to show whoever is in the staff
 * member's crosshair (see {@link StaffToolItems#updateHeldTool}), and calls
 * {@link StaffToolItems#restoreIfTampered} as the tool-kit tampering
 * backstop - see that method's javadoc for why this isn't the primary
 * defense (that's {@link StaffModeGuardListener}'s click/drag/drop
 * cancellation).
 *
 * <p>Re-renders the held tool every tick a target exists rather than only
 * on target-change, so a status flip (e.g. freezing the very player being
 * looked at while holding the Freeze Tool) shows up within one tick instead
 * of requiring the staff member to look away and back - cheap enough given
 * how few players are ever in Staff Mode at once.
 */
final class StaffModeTargetTracker {

    /** Matches the freeze/interact tools' own right-click reach - see {@code StaffModeGuardListener}. */
    private static final int TARGET_RANGE_BLOCKS = 6;

    private final StaffModeState staffModeState;
    private final StaffToolItems toolItems;
    private final FreezeRuntimeState freezeState;
    private final VanishService vanishService;

    StaffModeTargetTracker(StaffModeState staffModeState, StaffToolItems toolItems, FreezeRuntimeState freezeState, VanishService vanishService) {
        this.staffModeState = staffModeState;
        this.toolItems = toolItems;
        this.freezeState = freezeState;
        this.vanishService = vanishService;
    }

    void tick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!staffModeState.isActive(player.getUniqueId())) {
                continue;
            }
            toolItems.restoreIfTampered(player);

            Player target = targetedPlayer(player);
            boolean frozen = target != null && freezeState.isFrozen(target.getUniqueId());
            boolean vanished = target != null && vanishService.isVanished(target.getUniqueId());
            StaffToolItems.Tool held = toolItems.toolOf(player.getInventory().getItemInMainHand()).orElse(null);
            toolItems.updateHeldTool(player, held, target, frozen, vanished);
        }
    }

    private Player targetedPlayer(Player staff) {
        Entity entity = staff.getTargetEntity(TARGET_RANGE_BLOCKS);
        if (entity instanceof Player target && !target.getUniqueId().equals(staff.getUniqueId())) {
            return target;
        }
        return null;
    }
}

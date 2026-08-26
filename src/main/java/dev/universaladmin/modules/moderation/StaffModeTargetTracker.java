package dev.universaladmin.modules.moderation;

import dev.universaladmin.localization.ComponentMessages;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.localization.MessageService;
import dev.universaladmin.settings.SettingsService;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Runs periodically (see {@code ModerationModule#onEnable}'s repeating
 * task) for every online, Staff-Mode-active player: refreshes whichever
 * status-aware tool is currently held to show whoever is in the staff
 * member's crosshair (see {@link StaffToolItems#updateHeldTool}), sends a
 * persistent actionbar naming the same target, and calls
 * {@link StaffToolItems#restoreIfTampered} as the tool-kit tampering
 * backstop - see that method's javadoc for why this isn't the primary
 * defense (that's {@link StaffModeGuardListener}'s click/drag/drop
 * cancellation).
 *
 * <p>Re-renders the held tool/actionbar every tick a target exists rather
 * than only on target-change, so a status flip (e.g. freezing the very
 * player being looked at while holding the Freeze Tool) shows up within one
 * tick instead of requiring the staff member to look away and back - cheap
 * enough given how few players are ever in Staff Mode at once. Resending the
 * actionbar this often also keeps Minecraft's own fade-out timer from ever
 * expiring, which is what makes it read as "persistent" to the player.
 */
final class StaffModeTargetTracker {

    /**
     * The angular tolerance around the staff member's exact look direction a
     * candidate still counts as "being looked at" within - about the width
     * of a vanilla crosshair's forgiveness, translated to an angle since,
     * unlike {@link Player#getTargetEntity(int)}, this search has no vanilla
     * hitbox ray trace to lean on (see {@link #targetedPlayer} for why).
     */
    private static final double ANGLE_TOLERANCE_DEGREES = 10.0;
    private static final double COS_THRESHOLD = Math.cos(Math.toRadians(ANGLE_TOLERANCE_DEGREES));

    private final StaffModeState staffModeState;
    private final StaffToolItems toolItems;
    private final FreezeRuntimeState freezeState;
    private final VanishService vanishService;
    private final SettingsService settings;
    private final MessageService messages;

    StaffModeTargetTracker(
            StaffModeState staffModeState, StaffToolItems toolItems, FreezeRuntimeState freezeState,
            VanishService vanishService, SettingsService settings, MessageService messages) {
        this.staffModeState = staffModeState;
        this.toolItems = toolItems;
        this.freezeState = freezeState;
        this.vanishService = vanishService;
        this.settings = settings;
        this.messages = messages;
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
            player.sendActionBar(actionbarFor(target));
        }
    }

    private Component actionbarFor(Player target) {
        return target != null
                ? ComponentMessages.render(messages.get(MessageKey.of("moderation.gui.staffmode.actionbar.target"), target.getName()))
                : ComponentMessages.render(messages.get(MessageKey.of("moderation.gui.staffmode.actionbar.no-target")));
    }

    /**
     * A manual "look cone" search rather than {@link Player#getTargetEntity(int)}:
     * that method's block-collision ray trace both caps range at whatever's
     * passed in and stops at the first wall, neither of which staff want
     * here (see {@link ModerationSettings#STAFFMODE_TARGET_RANGE_BLOCKS}) -
     * this deliberately ignores blocks entirely, picking the online player
     * (within range, same world) whose direction from the staff member's eye
     * most closely matches their look direction, inside a narrow angular
     * tolerance so it still reads as "who I'm looking at" rather than
     * "who's nearby and roughly in front of me."
     */
    private Player targetedPlayer(Player staff) {
        double range = settings.get(ModerationSettings.STAFFMODE_TARGET_RANGE_BLOCKS);
        double rangeSquared = range * range;
        Location eye = staff.getEyeLocation();
        Vector direction = eye.getDirection().normalize();

        Player best = null;
        double bestDot = COS_THRESHOLD;
        for (Player candidate : Bukkit.getOnlinePlayers()) {
            if (candidate.getUniqueId().equals(staff.getUniqueId()) || !candidate.getWorld().equals(staff.getWorld())) {
                continue;
            }
            Location candidateEye = candidate.getEyeLocation();
            if (eye.distanceSquared(candidateEye) > rangeSquared) {
                continue;
            }
            Vector toCandidate = candidateEye.toVector().subtract(eye.toVector());
            if (toCandidate.lengthSquared() < 1.0e-6) {
                continue;
            }
            double dot = direction.dot(toCandidate.normalize());
            if (dot > bestDot) {
                bestDot = dot;
                best = candidate;
            }
        }
        return best;
    }
}

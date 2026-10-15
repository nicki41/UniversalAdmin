package dev.universaladmin.modules.moderation;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Who a Staff Mode player is aiming at - shared by the "looking at" actionbar
 * ({@link StaffModeTargetTracker}) and the tools themselves
 * ({@link StaffModeGuardListener}), so a tool always acts on exactly the
 * player the actionbar names, at any distance up to
 * {@link ModerationSettings#STAFFMODE_TARGET_RANGE_BLOCKS}.
 *
 * <p>A "look cone" search instead of {@link Player#getTargetEntity(int)}:
 * that ray trace stops at the first wall and only reaches as far as the
 * client's own interaction range, neither of which staff want - this
 * ignores blocks entirely and picks the online player (same world, within
 * range) whose direction from the staff member's eye is closest to their
 * look direction, inside a narrow angle so it still means "who I'm looking
 * at" rather than "who is roughly in front of me".
 */
final class StaffModeTargeting {

    /** About the forgiveness of a vanilla crosshair, as an angle. */
    private static final double ANGLE_TOLERANCE_DEGREES = 10.0;
    private static final double COS_THRESHOLD = Math.cos(Math.toRadians(ANGLE_TOLERANCE_DEGREES));

    private StaffModeTargeting() {
    }

    static Player targetedPlayer(Player staff, double range) {
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

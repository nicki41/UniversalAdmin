package dev.universaladmin.modules.moderation;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory "who currently has an active staff-mode session" -
 * {@code StaffModeGuardListener} reads this on every interact/pickup/break
 * event, never the database. The crash-safe half ({@code
 * staff_mode_snapshots}) is a separate, genuinely persistent concern - see
 * {@code StaffModeSnapshotRepository}.
 */
public final class StaffModeState {

    private final Set<UUID> active = ConcurrentHashMap.newKeySet();

    public boolean isActive(UUID playerId) {
        return active.contains(playerId);
    }

    public void setActive(UUID playerId, boolean value) {
        if (value) {
            active.add(playerId);
        } else {
            active.remove(playerId);
        }
    }
}

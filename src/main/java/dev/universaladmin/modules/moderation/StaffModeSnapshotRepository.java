package dev.universaladmin.modules.moderation;

import dev.universaladmin.storage.Repository;
import java.util.UUID;

/**
 * Persisted staff-mode snapshots, one row per player (existence = "has a
 * pending snapshot"). {@code save} is only ever called by {@code
 * StaffModeService#enter} after confirming {@code findById} is empty -
 * "if a snapshot already exists, don't blindly overwrite it" - this
 * interface itself does not enforce that guard, the service does.
 */
public interface StaffModeSnapshotRepository extends Repository<StaffModeSnapshot, UUID> {
}

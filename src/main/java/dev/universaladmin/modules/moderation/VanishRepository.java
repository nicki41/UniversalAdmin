package dev.universaladmin.modules.moderation;

import dev.universaladmin.storage.Repository;
import java.util.UUID;

/**
 * Persisted vanish state, for reconnect-restore only - {@code
 * VanishRuntimeState} (in-memory) is what every hot-path listener actually
 * reads. {@code findById(playerId).isPresent()} is "was vanished, restore
 * on reconnect if {@code vanish.restore-on-reconnect} is on";
 * {@code deleteById} clears it on unvanish.
 */
public interface VanishRepository extends Repository<VanishRecord, UUID> {
}

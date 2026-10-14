package dev.universaladmin.core;

import java.util.Objects;
import java.util.UUID;

/**
 * A player identified by UUID plus the best-known name for display - what a
 * command argument, a GUI selection, or a profile-menu entry resolves a
 * player to, whether that player is online or not.
 *
 * @param id   the player's UUID, the only stable identity
 * @param name the last known name, for display only - never used for lookups
 */
public record PlayerRef(UUID id, String name) {

    public PlayerRef {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
    }
}

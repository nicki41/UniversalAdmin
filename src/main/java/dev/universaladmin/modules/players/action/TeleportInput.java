package dev.universaladmin.modules.players.action;

import java.util.UUID;

/**
 * Input for {@code TeleportPlayerAction}. Which fields matter depends on
 * {@code kind}: {@code referenceId} only for {@link TeleportKind#PLAYER_TO_PLAYER},
 * {@code x/y/z} (and optional {@code worldName}, else the target's current
 * world) only for {@link TeleportKind#COORDINATES}. Unused fields are
 * {@code null}/{@code 0}.
 */
public record TeleportInput(
        TeleportKind kind, UUID targetId, UUID referenceId, String worldName, double x, double y, double z) {

    public static TeleportInput of(TeleportKind kind, UUID targetId) {
        return new TeleportInput(kind, targetId, null, null, 0, 0, 0);
    }

    public static TeleportInput toPlayer(UUID targetId, UUID referenceId) {
        return new TeleportInput(TeleportKind.PLAYER_TO_PLAYER, targetId, referenceId, null, 0, 0, 0);
    }

    public static TeleportInput toCoordinates(UUID targetId, String worldName, double x, double y, double z) {
        return new TeleportInput(TeleportKind.COORDINATES, targetId, null, worldName, x, y, z);
    }
}

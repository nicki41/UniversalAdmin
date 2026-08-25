package dev.universaladmin.modules.players.action;

/**
 * The six teleport variants the Players module offers, sharing one {@code
 * TeleportPlayerAction}/permission node (see docs/user/modules/players.md)
 * but distinguished in the audit summary.
 */
public enum TeleportKind {
    /** Actor teleports to {@code targetId}'s location. */
    ADMIN_TO_PLAYER,
    /** {@code targetId} is teleported to the actor's location. */
    BRING_TO_ADMIN,
    /** {@code targetId} is teleported to {@code referenceId}'s location. */
    PLAYER_TO_PLAYER,
    /** {@code targetId} is teleported to their current world's spawn. */
    WORLD_SPAWN,
    /** {@code targetId} is teleported to their bed/respawn location. */
    BED_RESPAWN,
    /** {@code targetId} is teleported to explicit coordinates. */
    COORDINATES
}

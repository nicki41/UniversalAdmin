package dev.universaladmin.action;

/**
 * Where an {@link Actor} performing an {@link Action} came from. Kept
 * separate from any single frontend (Bukkit {@code CommandSender}, a future
 * web session, ...) so actions never depend on Paper types for "who is doing
 * this".
 */
public enum ActorType {
    PLAYER,
    CONSOLE,
    /** A future web-app session, authenticated separately from the game server. */
    WEB,
    /** The system itself (scheduled tasks, migrations, startup routines). */
    SYSTEM,
    /**
     * Someone acting through another application connected to UniversalAdmin -
     * e.g. a Discord user through the Discord bot - authenticated and given
     * permissions by that application.
     *
     * @since 1.2
     */
    REMOTE
}

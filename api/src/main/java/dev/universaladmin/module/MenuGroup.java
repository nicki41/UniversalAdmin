package dev.universaladmin.module;

/**
 * The row of the {@code /admin} main menu a module's button sits in -
 * modules of the same kind side by side, each row headed by a coloured pane
 * that names it.
 *
 * @since 1.7
 */
public enum MenuGroup {
    /** Players, moderation, reports, chat, nicks. */
    PLAYERS,
    /** The server, its worlds, the whitelist, performance, the network. */
    SERVER,
    /** The audit log, settings - and every extension that does not choose a group. */
    SYSTEM
}

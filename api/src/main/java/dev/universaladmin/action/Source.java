package dev.universaladmin.action;

/**
 * The channel an {@link Actor} used to invoke an {@link Action} through -
 * kept separate from {@link Actor} itself because the same actor can invoke
 * different requests from different sources over time (a player using both
 * the GUI and {@code /admin} commands in the same session).
 */
public enum Source {
    GUI,
    COMMAND,
    WEB,
    API,
    EXTENSION,
    SYSTEM
}

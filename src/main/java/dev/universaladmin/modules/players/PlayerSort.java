package dev.universaladmin.modules.players;

/** How {@link PlayerProfileRepository#search} orders its results. */
public enum PlayerSort {
    NAME_ASC,
    NAME_DESC,
    LAST_SEEN_DESC,
    LAST_SEEN_ASC,
    FIRST_JOIN_DESC
}

package dev.universaladmin.modules.players;

/**
 * Query for {@link PlayerProfileRepository#search} - the bounded, indexed
 * alternative to {@code findAll()} that backs the Offline Players/Search/
 * Recently Seen GUI lists (see docs/user/modules/players.md). {@code
 * nameContains} is matched case-insensitively; {@code null} or blank means
 * "no name filter". {@code limit} must be positive - callers pass
 * {@code PlayersSettings.GUI_MAX_RESULTS} unless they have a smaller,
 * feature-specific bound.
 */
public record PlayerSearchQuery(String nameContains, PlayerSort sort, int limit) {

    public PlayerSearchQuery {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive, was " + limit);
        }
    }

    public static PlayerSearchQuery of(String nameContains, PlayerSort sort, int limit) {
        return new PlayerSearchQuery(nameContains, sort, limit);
    }
}

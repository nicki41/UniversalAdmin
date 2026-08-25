package dev.universaladmin.modules.players;

import dev.universaladmin.storage.Migration;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Adds the indexes {@link dev.universaladmin.modules.players.jdbc.JdbcPlayerProfileRepository#search}
 * relies on for its {@code ORDER BY} paths (name, last-seen) - version 1001,
 * right after {@link PlayerProfileMigration}'s 1000. The substring {@code
 * LIKE} filter itself doesn't benefit from either index; see the comment on
 * {@code search(...)} for why that's an accepted simplification.
 */
public final class PlayerProfileIndexMigration implements Migration {

    @Override
    public int version() {
        return 1001;
    }

    @Override
    public String description() {
        return "Index player_profiles for name/last-seen ordering";
    }

    @Override
    public void migrate(Connection connection) throws SQLException {
        // No "IF NOT EXISTS" here: MigrationRunner already guarantees this
        // runs at most once, and real MySQL (unlike SQLite/MariaDB) rejects
        // that clause on CREATE INDEX - see docs/architecture/storage.md#dialekt-unterschiede.
        try (Statement statement = connection.createStatement()) {
            statement.execute(
                    "CREATE INDEX idx_player_profiles_last_known_name ON player_profiles(last_known_name)");
            statement.execute(
                    "CREATE INDEX idx_player_profiles_last_seen ON player_profiles(last_seen)");
        }
    }
}

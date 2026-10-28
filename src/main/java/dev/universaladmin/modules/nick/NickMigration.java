package dev.universaladmin.modules.nick;

import dev.universaladmin.storage.Migration;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Creates {@code ua_nicks} - version 1017. {@code name_key} is the
 * lower-case nick; its unique index keeps two players from getting the
 * same nick, also when two servers of a network try at the same moment.
 */
public final class NickMigration implements Migration {

    @Override
    public int version() {
        return 1017;
    }

    @Override
    public String description() {
        return "Create ua_nicks (nick system)";
    }

    @Override
    public void migrate(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS ua_nicks (
                        player_id VARCHAR(36) PRIMARY KEY,
                        real_name VARCHAR(16) NOT NULL,
                        name VARCHAR(16) NOT NULL,
                        name_key VARCHAR(16) NOT NULL,
                        skin_owner VARCHAR(16),
                        skin_value TEXT,
                        skin_signature TEXT,
                        created_at BIGINT NOT NULL
                    )
                    """);
            // No "IF NOT EXISTS" on the index: real MySQL rejects it, and
            // MigrationRunner runs this at most once.
            statement.execute("CREATE UNIQUE INDEX idx_nicks_name ON ua_nicks(name_key)");
        }
    }
}

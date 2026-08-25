package dev.universaladmin.modules.players;

import dev.universaladmin.storage.Migration;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Creates the {@code player_profiles} table. Module migrations start at
 * version 1000 (see {@link Migration}) to leave room for core migrations
 * below them.
 */
public final class PlayerProfileMigration implements Migration {

    @Override
    public int version() {
        return 1000;
    }

    @Override
    public String description() {
        return "Create player_profiles table";
    }

    @Override
    public void migrate(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS player_profiles (
                        id VARCHAR(36) PRIMARY KEY,
                        last_known_name VARCHAR(16) NOT NULL,
                        first_join BIGINT NOT NULL,
                        last_seen BIGINT NOT NULL
                    )
                    """);
        }
    }
}

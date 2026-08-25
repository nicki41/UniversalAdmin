package dev.universaladmin.modules.whitelist;

import dev.universaladmin.storage.Migration;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/** Creates the {@code whitelist_entries} table - version 1007, right after the Server module's 1006. */
public final class WhitelistMigration implements Migration {

    @Override
    public int version() {
        return 1007;
    }

    @Override
    public String description() {
        return "Create whitelist_entries table";
    }

    @Override
    public void migrate(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS whitelist_entries (
                        player_id VARCHAR(36) PRIMARY KEY,
                        player_name VARCHAR(16) NOT NULL,
                        source VARCHAR(32) NOT NULL,
                        added_by_id VARCHAR(36),
                        added_by_name VARCHAR(64) NOT NULL,
                        added_at BIGINT NOT NULL,
                        reason VARCHAR(255),
                        notes VARCHAR(255),
                        expires_at BIGINT
                    )
                    """);
        }
    }
}

package dev.universaladmin.modules.server;

import dev.universaladmin.storage.Migration;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/** Creates the {@code server_maintenance_state} single-row table - version 1006, right after {@code StaffModeSnapshotMigration}'s 1005. */
public final class ServerMaintenanceMigration implements Migration {

    @Override
    public int version() {
        return 1006;
    }

    @Override
    public String description() {
        return "Create server_maintenance_state table";
    }

    @Override
    public void migrate(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS server_maintenance_state (
                        id INTEGER PRIMARY KEY,
                        enabled BOOLEAN NOT NULL,
                        reason VARCHAR(255),
                        message VARCHAR(500),
                        allowed_players TEXT NOT NULL,
                        updated_at BIGINT NOT NULL,
                        updated_by VARCHAR(64)
                    )
                    """);
        }
    }
}

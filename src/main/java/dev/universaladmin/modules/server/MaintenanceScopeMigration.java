package dev.universaladmin.modules.server;

import dev.universaladmin.storage.Migration;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Creates {@code ua_maintenance} - one maintenance state per scope key
 * instead of {@code server_maintenance_state}'s single row, which every
 * server of a network shared (one server's maintenance became every
 * server's after a restart). Version 1012.
 *
 * <p>The old row is copied to the {@code local} scope.
 * {@link DefaultMaintenanceService} falls back to it for a network server
 * without a row of its own yet, so an upgrade keeps every server's
 * maintenance as it was. The old table stays (a downgrade still finds it).
 */
public final class MaintenanceScopeMigration implements Migration {

    @Override
    public int version() {
        return 1012;
    }

    @Override
    public String description() {
        return "Create ua_maintenance table (maintenance per server or network)";
    }

    @Override
    public void migrate(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS ua_maintenance (
                        scope VARCHAR(100) PRIMARY KEY,
                        enabled BOOLEAN NOT NULL,
                        reason VARCHAR(255),
                        message VARCHAR(500),
                        allowed_players TEXT NOT NULL,
                        updated_at BIGINT NOT NULL,
                        updated_by VARCHAR(64)
                    )
                    """);
            boolean hasLocal;
            try (ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM ua_maintenance WHERE scope = 'local'")) {
                hasLocal = result.next() && result.getInt(1) > 0;
            }
            if (!hasLocal) {
                statement.executeUpdate("""
                        INSERT INTO ua_maintenance (scope, enabled, reason, message, allowed_players, updated_at, updated_by)
                        SELECT 'local', enabled, reason, message, allowed_players, updated_at, updated_by
                        FROM server_maintenance_state WHERE id = 1
                        """);
            }
        }
    }
}

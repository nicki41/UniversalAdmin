package dev.universaladmin.modules.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.universaladmin.storage.DatabaseConfig;
import dev.universaladmin.storage.MigrationRunner;
import dev.universaladmin.storage.jdbc.DataSourceFactory;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.logging.Logger;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Runs {@link ServerMaintenanceMigration} against a real temporary SQLite database - see {@code MigrationRunnerTest}'s precedent. */
class ServerMaintenanceMigrationTest {

    @Test
    void createsTheServerMaintenanceStateTableIdempotently(@TempDir Path dir) throws Exception {
        DataSource dataSource = DataSourceFactory.create(DatabaseConfig.sqlite("test.db"), dir);
        try {
            MigrationRunner runner = new MigrationRunner(dataSource, Logger.getLogger("test"));
            runner.register(new ServerMaintenanceMigration());

            runner.runPending();
            runner.runPending(); // must be idempotent

            try (Connection connection = dataSource.getConnection();
                    PreparedStatement insert = connection.prepareStatement(
                            "INSERT INTO server_maintenance_state (id, enabled, reason, message, allowed_players, updated_at, updated_by) "
                                    + "VALUES (1, 1, 'reason', 'message', 'alice,bob', 123, 'tester')")) {
                insert.executeUpdate();
            }
            try (Connection connection = dataSource.getConnection();
                    Statement statement = connection.createStatement();
                    ResultSet resultSet = statement.executeQuery("SELECT enabled, reason, allowed_players FROM server_maintenance_state WHERE id = 1")) {
                resultSet.next();
                assertEquals(true, resultSet.getBoolean("enabled"));
                assertEquals("reason", resultSet.getString("reason"));
                assertEquals("alice,bob", resultSet.getString("allowed_players"));
            }
        } finally {
            ((AutoCloseable) dataSource).close();
        }
    }
}

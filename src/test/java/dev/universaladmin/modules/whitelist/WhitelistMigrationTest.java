package dev.universaladmin.modules.whitelist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.universaladmin.storage.DatabaseConfig;
import dev.universaladmin.storage.MigrationRunner;
import dev.universaladmin.storage.jdbc.DataSourceFactory;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;
import java.util.logging.Logger;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Runs {@link WhitelistMigration} against a real temporary SQLite database - see {@code ServerMaintenanceMigrationTest}'s precedent. */
class WhitelistMigrationTest {

    @Test
    void createsTheWhitelistEntriesTableIdempotently(@TempDir Path dir) throws Exception {
        DataSource dataSource = DataSourceFactory.create(DatabaseConfig.sqlite("test.db"), dir);
        try {
            MigrationRunner runner = new MigrationRunner(dataSource, Logger.getLogger("test"));
            runner.register(new WhitelistMigration());

            runner.runPending();
            runner.runPending(); // must be idempotent

            try (Connection connection = dataSource.getConnection();
                    PreparedStatement insert = connection.prepareStatement(
                            "INSERT INTO whitelist_entries (player_id, player_name, source, added_by_name, added_at) "
                                    + "VALUES (?, 'Notch', 'UNIVERSAL_ADMIN', 'tester', ?)")) {
                insert.setString(1, UUID.randomUUID().toString());
                insert.setLong(2, System.currentTimeMillis());
                insert.executeUpdate();
            }
            try (Connection connection = dataSource.getConnection();
                    PreparedStatement select = connection.prepareStatement(
                            "SELECT player_name, expires_at FROM whitelist_entries WHERE player_name = 'Notch'");
                    ResultSet resultSet = select.executeQuery()) {
                resultSet.next();
                assertEquals("Notch", resultSet.getString("player_name"));
                resultSet.getLong("expires_at");
                assertTrue(resultSet.wasNull(), "expires_at should default to NULL when omitted");
            }
        } finally {
            ((AutoCloseable) dataSource).close();
        }
    }
}

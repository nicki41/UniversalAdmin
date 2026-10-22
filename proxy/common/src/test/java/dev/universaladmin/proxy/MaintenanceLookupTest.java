package dev.universaladmin.proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;

/**
 * The proxy's read of the network's maintenance against {@code ua_maintenance}
 * as the plugin's own migration creates it (column layout copied from
 * {@code MaintenanceScopeMigration}).
 */
class MaintenanceLookupTest {

    private static final long MAX_AGE = 3_000;

    @TempDir
    Path dir;

    private SQLiteDataSource dataSource() {
        SQLiteDataSource sqlite = new SQLiteDataSource();
        sqlite.setUrl("jdbc:sqlite:" + dir.resolve("maintenance.db"));
        return sqlite;
    }

    private void createTable(SQLiteDataSource dataSource) throws SQLException {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE ua_maintenance (
                        scope VARCHAR(100) PRIMARY KEY,
                        enabled BOOLEAN NOT NULL,
                        reason VARCHAR(255),
                        message VARCHAR(500),
                        allowed_players TEXT NOT NULL,
                        updated_at BIGINT NOT NULL,
                        updated_by VARCHAR(64)
                    )""");
        }
    }

    private void put(SQLiteDataSource dataSource, String scope, boolean enabled, String message, String allowed) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT OR REPLACE INTO ua_maintenance (scope, enabled, reason, message, allowed_players, updated_at, updated_by) "
                                + "VALUES (?, ?, 'reason', ?, ?, 1, 'tester')")) {
            statement.setString(1, scope);
            statement.setBoolean(2, enabled);
            statement.setString(3, message);
            statement.setString(4, allowed);
            statement.executeUpdate();
        }
    }

    @Test
    void noRowMeansNoMaintenance() throws SQLException {
        SQLiteDataSource dataSource = dataSource();
        createTable(dataSource);

        assertFalse(new MaintenanceLookup(dataSource, MAX_AGE).current(0).enabled());
    }

    @Test
    void readsTheNetworkRowOnlyWithItsAllowList() throws SQLException {
        SQLiteDataSource dataSource = dataSource();
        createTable(dataSource);
        put(dataSource, "server:lobby", true, null, "");
        put(dataSource, "network", true, "<red>Closed", "Alice,bob");

        MaintenanceLookup.Maintenance maintenance = new MaintenanceLookup(dataSource, MAX_AGE).current(0);

        assertTrue(maintenance.enabled());
        assertEquals("<red>Closed", maintenance.message());
        assertTrue(maintenance.allows("alice"));
        assertTrue(maintenance.allows("BOB"));
        assertFalse(maintenance.allows("carol"));
    }

    @Test
    void aServersOwnMaintenanceDoesNotCloseTheNetwork() throws SQLException {
        SQLiteDataSource dataSource = dataSource();
        createTable(dataSource);
        put(dataSource, "server:survival", true, null, "");

        assertFalse(new MaintenanceLookup(dataSource, MAX_AGE).current(0).enabled());
    }

    @Test
    void theAnswerIsKeptForAFewSeconds() throws SQLException {
        SQLiteDataSource dataSource = dataSource();
        createTable(dataSource);
        MaintenanceLookup lookup = new MaintenanceLookup(dataSource, MAX_AGE);
        assertFalse(lookup.current(0).enabled());

        put(dataSource, "network", true, null, "");

        assertFalse(lookup.current(MAX_AGE - 1).enabled());
        assertTrue(lookup.current(MAX_AGE).enabled());
    }

    @Test
    void aMissingTableCountsAsNoMaintenance() {
        MaintenanceLookup lookup = new MaintenanceLookup(dataSource(), MAX_AGE);

        assertFalse(lookup.current(0).enabled());
        assertNotNull(lookup.lastError());
    }

    @Test
    void aFailedReadIsRetriedLaterAndASuccessfulOneClearsTheError() throws SQLException {
        SQLiteDataSource dataSource = dataSource();
        MaintenanceLookup lookup = new MaintenanceLookup(dataSource, MAX_AGE);
        lookup.current(0);
        assertNotNull(lookup.lastError());

        createTable(dataSource);

        lookup.current(MaintenanceLookup.RETRY_AFTER_FAILURE_MILLIS - 1);
        assertNotNull(lookup.lastError());
        lookup.current(MaintenanceLookup.RETRY_AFTER_FAILURE_MILLIS);
        assertNull(lookup.lastError());
    }
}

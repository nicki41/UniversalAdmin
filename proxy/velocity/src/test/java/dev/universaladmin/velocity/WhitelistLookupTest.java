package dev.universaladmin.velocity;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;

/**
 * The proxy's read of the network whitelist against {@code ua_whitelist} and
 * {@code ua_whitelist_state} as the plugin's migration creates them (column
 * layout copied from {@code WhitelistScopeMigration}).
 */
class WhitelistLookupTest {

    private static final long NOW = 1_000_000;
    private static final UUID STEVE = UUID.randomUUID();
    private static final UUID ALEX = UUID.randomUUID();

    @TempDir
    Path dir;

    private SQLiteDataSource dataSource() {
        SQLiteDataSource sqlite = new SQLiteDataSource();
        sqlite.setUrl("jdbc:sqlite:" + dir.resolve("whitelist.db"));
        return sqlite;
    }

    private void execute(SQLiteDataSource dataSource, String... sql) throws SQLException {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            for (String line : sql) {
                statement.execute(line);
            }
        }
    }

    private SQLiteDataSource withTables() throws SQLException {
        SQLiteDataSource dataSource = dataSource();
        execute(dataSource, """
                CREATE TABLE ua_whitelist (
                    scope VARCHAR(100) NOT NULL, player_id VARCHAR(36) NOT NULL, player_name VARCHAR(16) NOT NULL,
                    source VARCHAR(32) NOT NULL, added_by_id VARCHAR(36), added_by_name VARCHAR(64) NOT NULL,
                    added_at BIGINT NOT NULL, reason VARCHAR(255), notes VARCHAR(255), expires_at BIGINT,
                    PRIMARY KEY (scope, player_id))""", """
                CREATE TABLE ua_whitelist_state (
                    scope VARCHAR(100) PRIMARY KEY, enabled BOOLEAN NOT NULL, updated_at BIGINT NOT NULL, updated_by VARCHAR(64))""");
        return dataSource;
    }

    @Test
    void offUntilSwitchedOn() throws SQLException {
        SQLiteDataSource dataSource = withTables();
        WhitelistLookup lookup = new WhitelistLookup(dataSource, 0);
        assertFalse(lookup.enabled(NOW));

        execute(dataSource, "INSERT INTO ua_whitelist_state VALUES ('network', 1, 1, 'Admin')");

        assertTrue(lookup.enabled(NOW + 1));
    }

    @Test
    void onlyValidNetworkEntriesCount() throws SQLException {
        SQLiteDataSource dataSource = withTables();
        execute(dataSource,
                "INSERT INTO ua_whitelist VALUES ('network', '" + STEVE + "', 'Steve', 'UNIVERSAL_ADMIN', NULL, 'Admin', 1, NULL, NULL, NULL)",
                "INSERT INTO ua_whitelist VALUES ('network', '" + ALEX + "', 'Alex', 'UNIVERSAL_ADMIN', NULL, 'Admin', 1, NULL, NULL, " + (NOW - 1) + ")",
                "INSERT INTO ua_whitelist VALUES ('server:lobby', '" + ALEX + "', 'Alex', 'UNIVERSAL_ADMIN', NULL, 'Admin', 1, NULL, NULL, NULL)");
        WhitelistLookup lookup = new WhitelistLookup(dataSource, 0);

        assertTrue(lookup.listed(STEVE, NOW));
        assertFalse(lookup.listed(ALEX, NOW));
        assertFalse(lookup.listed(UUID.randomUUID(), NOW));
    }

    @Test
    void missingTablesFailOpen() {
        WhitelistLookup lookup = new WhitelistLookup(dataSource(), 0);

        assertFalse(lookup.enabled(NOW));
        assertTrue(lookup.listed(STEVE, NOW));
        assertNotNull(lookup.lastError());
    }
}

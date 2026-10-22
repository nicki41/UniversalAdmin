package dev.universaladmin.modules.whitelist;

import dev.universaladmin.storage.Migration;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Creates {@code ua_whitelist} - whitelist metadata per scope key ({@code
 * local}, {@code server:<name>}, {@code network}) instead of
 * {@code whitelist_entries}, which every server of a network shared (one
 * server's expiry sweep could un-whitelist a player on another) - and
 * {@code ua_whitelist_state} for the network whitelist's on/off switch.
 * Version 1014.
 *
 * <p>The old rows are copied to the {@code local} scope; a network server
 * takes over the ones for players on its own native whitelist at startup
 * ({@link WhitelistEntryRepository#adopt}). The old table stays for a downgrade.
 */
public final class WhitelistScopeMigration implements Migration {

    @Override
    public int version() {
        return 1014;
    }

    @Override
    public String description() {
        return "Create ua_whitelist and ua_whitelist_state (whitelist per server or network)";
    }

    @Override
    public void migrate(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS ua_whitelist (
                        scope VARCHAR(100) NOT NULL,
                        player_id VARCHAR(36) NOT NULL,
                        player_name VARCHAR(16) NOT NULL,
                        source VARCHAR(32) NOT NULL,
                        added_by_id VARCHAR(36),
                        added_by_name VARCHAR(64) NOT NULL,
                        added_at BIGINT NOT NULL,
                        reason VARCHAR(255),
                        notes VARCHAR(255),
                        expires_at BIGINT,
                        PRIMARY KEY (scope, player_id)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS ua_whitelist_state (
                        scope VARCHAR(100) PRIMARY KEY,
                        enabled BOOLEAN NOT NULL,
                        updated_at BIGINT NOT NULL,
                        updated_by VARCHAR(64)
                    )
                    """);
            statement.executeUpdate("""
                    INSERT INTO ua_whitelist (scope, player_id, player_name, source, added_by_id, added_by_name, added_at,
                        reason, notes, expires_at)
                    SELECT 'local', player_id, player_name, source, added_by_id, added_by_name, added_at, reason, notes, expires_at
                    FROM whitelist_entries
                    """);
        }
    }
}

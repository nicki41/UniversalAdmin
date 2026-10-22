package dev.universaladmin.modules.moderation.alts;

import dev.universaladmin.storage.Migration;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Creates the tables of alt detection - version 1015:
 * {@code ua_player_addresses} (which player connected from which address,
 * stored only as a keyed hash, see {@link AddressHasher}) and
 * {@code ua_alt_key} (the one random key of that hash, shared by every
 * server of a network so they match the same addresses).
 */
public final class AltsMigration implements Migration {

    @Override
    public int version() {
        return 1015;
    }

    @Override
    public String description() {
        return "Create ua_player_addresses and ua_alt_key (alt detection)";
    }

    @Override
    public void migrate(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS ua_player_addresses (
                        player_id VARCHAR(36) NOT NULL,
                        address_hash VARCHAR(64) NOT NULL,
                        player_name VARCHAR(16) NOT NULL,
                        first_seen BIGINT NOT NULL,
                        last_seen BIGINT NOT NULL,
                        PRIMARY KEY (player_id, address_hash)
                    )
                    """);
            // No "IF NOT EXISTS" on the index: real MySQL rejects it, and
            // MigrationRunner runs this at most once.
            statement.execute("CREATE INDEX idx_player_addresses_hash ON ua_player_addresses(address_hash)");
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS ua_alt_key (
                        id INTEGER PRIMARY KEY,
                        secret VARCHAR(128) NOT NULL
                    )
                    """);
        }
    }
}

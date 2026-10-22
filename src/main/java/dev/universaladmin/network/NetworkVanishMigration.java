package dev.universaladmin.network;

import dev.universaladmin.storage.Migration;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Adds {@code ua_network_players.vanished} - whether a player is vanished on
 * their server, so the other servers can leave them out of what they show
 * (see {@link NetworkService#vanished}). Core version 4, after
 * {@link NetworkSchemaMigration}'s 3. Servers that still run an older
 * version insert rows without it and get the default.
 */
public final class NetworkVanishMigration implements Migration {

    @Override
    public int version() {
        return 4;
    }

    @Override
    public String description() {
        return "Add ua_network_players.vanished";
    }

    @Override
    public void migrate(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE ua_network_players ADD COLUMN vanished BOOLEAN NOT NULL DEFAULT FALSE");
        }
    }
}

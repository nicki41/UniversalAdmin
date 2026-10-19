package dev.universaladmin.network;

import dev.universaladmin.storage.Migration;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * The three tables of network mode: which servers are up
 * ({@code ua_network_servers}), who is on which server
 * ({@code ua_network_players}) and the message channel between the servers
 * ({@code ua_network_messages}). A core migration (core versions stay below
 * 1000, see {@link Migration}); created on every server, used only while
 * network mode is on.
 */
public final class NetworkSchemaMigration implements Migration {

    @Override
    public int version() {
        return 3;
    }

    @Override
    public String description() {
        return "Create ua_network_servers, ua_network_players and ua_network_messages tables";
    }

    @Override
    public void migrate(Connection connection) throws SQLException {
        boolean sqlite = connection.getMetaData().getDatabaseProductName().toLowerCase().contains("sqlite");
        String idColumn = sqlite ? "id INTEGER PRIMARY KEY AUTOINCREMENT" : "id BIGINT AUTO_INCREMENT PRIMARY KEY";
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS ua_network_servers (
                        name VARCHAR(64) NOT NULL PRIMARY KEY,
                        online_players INT NOT NULL,
                        max_players INT NOT NULL,
                        tps DOUBLE NOT NULL,
                        version VARCHAR(64),
                        started_at BIGINT NOT NULL,
                        last_seen BIGINT NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS ua_network_players (
                        player_id VARCHAR(36) NOT NULL PRIMARY KEY,
                        name VARCHAR(16) NOT NULL,
                        server VARCHAR(64) NOT NULL,
                        joined_at BIGINT NOT NULL
                    )
                    """);
            statement.execute("CREATE INDEX ua_network_players_server ON ua_network_players (server)");
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS ua_network_messages (
                        %s,
                        created_at BIGINT NOT NULL,
                        source VARCHAR(64) NOT NULL,
                        target VARCHAR(64),
                        topic VARCHAR(128) NOT NULL,
                        payload TEXT NOT NULL
                    )
                    """.formatted(idColumn));
            statement.execute("CREATE INDEX ua_network_messages_created ON ua_network_messages (created_at)");
        }
    }
}

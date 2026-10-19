package dev.universaladmin.network.jdbc;

import dev.universaladmin.network.NetworkRepository;
import dev.universaladmin.scheduler.TaskScheduler;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import javax.sql.DataSource;

/**
 * JDBC {@link NetworkRepository}. Plain, portable SQL (update-then-insert
 * instead of MySQL's {@code ON DUPLICATE KEY}), so the same code is tested
 * against SQLite and runs against the MySQL/MariaDB database network mode
 * requires.
 */
public final class JdbcNetworkRepository implements NetworkRepository {

    private final DataSource dataSource;
    private final TaskScheduler scheduler;

    public JdbcNetworkRepository(DataSource dataSource, TaskScheduler scheduler) {
        this.dataSource = dataSource;
        this.scheduler = scheduler;
    }

    @Override
    public CompletableFuture<Void> heartbeat(ServerRow server, Collection<PlayerRow> players) {
        return scheduler.runAsync(() -> inTransaction(connection -> {
            try (PreparedStatement update = connection.prepareStatement("""
                    UPDATE ua_network_servers SET online_players = ?, max_players = ?, tps = ?, version = ?, started_at = ?, last_seen = ?
                    WHERE name = ?""")) {
                update.setInt(1, server.onlinePlayers());
                update.setInt(2, server.maxPlayers());
                update.setDouble(3, server.tps());
                update.setString(4, server.version());
                update.setLong(5, server.startedAt().toEpochMilli());
                update.setLong(6, server.lastSeen().toEpochMilli());
                update.setString(7, server.name());
                if (update.executeUpdate() == 0) {
                    try (PreparedStatement insert = connection.prepareStatement("""
                            INSERT INTO ua_network_servers (name, online_players, max_players, tps, version, started_at, last_seen)
                            VALUES (?, ?, ?, ?, ?, ?, ?)""")) {
                        insert.setString(1, server.name());
                        insert.setInt(2, server.onlinePlayers());
                        insert.setInt(3, server.maxPlayers());
                        insert.setDouble(4, server.tps());
                        insert.setString(5, server.version());
                        insert.setLong(6, server.startedAt().toEpochMilli());
                        insert.setLong(7, server.lastSeen().toEpochMilli());
                        insert.executeUpdate();
                    }
                }
            }
            try (PreparedStatement delete = connection.prepareStatement("DELETE FROM ua_network_players WHERE server = ?")) {
                delete.setString(1, server.name());
                delete.executeUpdate();
            }
            for (PlayerRow player : players) {
                upsertPlayer(connection, player);
            }
        }));
    }

    @Override
    public CompletableFuture<Void> playerJoined(PlayerRow player) {
        return scheduler.runAsync(() -> inTransaction(connection -> upsertPlayer(connection, player)));
    }

    /** A player is on one server at a time: whichever server saw them last owns the row. */
    private static void upsertPlayer(Connection connection, PlayerRow player) throws SQLException {
        try (PreparedStatement delete = connection.prepareStatement("DELETE FROM ua_network_players WHERE player_id = ?")) {
            delete.setString(1, player.playerId().toString());
            delete.executeUpdate();
        }
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO ua_network_players (player_id, name, server, joined_at) VALUES (?, ?, ?, ?)")) {
            insert.setString(1, player.playerId().toString());
            insert.setString(2, player.name());
            insert.setString(3, player.server());
            insert.setLong(4, player.joinedAt().toEpochMilli());
            insert.executeUpdate();
        }
    }

    @Override
    public CompletableFuture<Void> playerLeft(UUID playerId, String server) {
        return scheduler.runAsync(() -> inTransaction(connection -> {
            // Only this server's row: when switching servers the new server
            // may already have registered the player before we see the quit.
            try (PreparedStatement delete = connection.prepareStatement(
                    "DELETE FROM ua_network_players WHERE player_id = ? AND server = ?")) {
                delete.setString(1, playerId.toString());
                delete.setString(2, server);
                delete.executeUpdate();
            }
        }));
    }

    @Override
    public CompletableFuture<Void> removeServer(String server) {
        return scheduler.runAsync(() -> inTransaction(connection -> {
            try (PreparedStatement players = connection.prepareStatement("DELETE FROM ua_network_players WHERE server = ?");
                    PreparedStatement servers = connection.prepareStatement("DELETE FROM ua_network_servers WHERE name = ?")) {
                players.setString(1, server);
                players.executeUpdate();
                servers.setString(1, server);
                servers.executeUpdate();
            }
        }));
    }

    @Override
    public CompletableFuture<List<ServerRow>> servers() {
        return scheduler.supplyAsync(() -> query("SELECT * FROM ua_network_servers ORDER BY name", statement -> { }, resultSet ->
                new ServerRow(resultSet.getString("name"), resultSet.getInt("online_players"), resultSet.getInt("max_players"),
                        resultSet.getDouble("tps"), resultSet.getString("version"),
                        Instant.ofEpochMilli(resultSet.getLong("started_at")), Instant.ofEpochMilli(resultSet.getLong("last_seen")))));
    }

    @Override
    public CompletableFuture<List<PlayerRow>> players() {
        return scheduler.supplyAsync(() -> query("SELECT * FROM ua_network_players ORDER BY name", statement -> { }, resultSet ->
                new PlayerRow(UUID.fromString(resultSet.getString("player_id")), resultSet.getString("name"),
                        resultSet.getString("server"), Instant.ofEpochMilli(resultSet.getLong("joined_at")))));
    }

    @Override
    public CompletableFuture<Void> publish(String source, String target, String topic, String payload, Instant createdAt) {
        return scheduler.runAsync(() -> inTransaction(connection -> {
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO ua_network_messages (created_at, source, target, topic, payload) VALUES (?, ?, ?, ?, ?)")) {
                insert.setLong(1, createdAt.toEpochMilli());
                insert.setString(2, source);
                insert.setString(3, target);
                insert.setString(4, topic);
                insert.setString(5, payload);
                insert.executeUpdate();
            }
        }));
    }

    @Override
    public CompletableFuture<Long> latestMessageId() {
        return scheduler.supplyAsync(() -> {
            List<Long> ids = query("SELECT MAX(id) AS max_id FROM ua_network_messages", statement -> { },
                    resultSet -> resultSet.getLong("max_id"));
            return ids.isEmpty() ? 0L : ids.get(0);
        });
    }

    @Override
    public CompletableFuture<List<MessageRow>> messagesSince(long afterId, String server, int limit) {
        return scheduler.supplyAsync(() -> query("""
                SELECT * FROM ua_network_messages
                WHERE id > ? AND source <> ? AND (target IS NULL OR target = ?)
                ORDER BY id LIMIT ?""", statement -> {
                    statement.setLong(1, afterId);
                    statement.setString(2, server);
                    statement.setString(3, server);
                    statement.setInt(4, limit);
                }, resultSet -> new MessageRow(resultSet.getLong("id"), Instant.ofEpochMilli(resultSet.getLong("created_at")),
                        resultSet.getString("source"), resultSet.getString("target"), resultSet.getString("topic"),
                        resultSet.getString("payload"))));
    }

    @Override
    public CompletableFuture<Integer> pruneMessages(Instant before) {
        return scheduler.supplyAsync(() -> {
            try (Connection connection = dataSource.getConnection();
                    PreparedStatement delete = connection.prepareStatement("DELETE FROM ua_network_messages WHERE created_at < ?")) {
                delete.setLong(1, before.toEpochMilli());
                return delete.executeUpdate();
            } catch (SQLException e) {
                throw new NetworkStorageException("Failed to prune network messages", e);
            }
        });
    }

    @FunctionalInterface
    private interface SqlWork {
        void run(Connection connection) throws SQLException;
    }

    @FunctionalInterface
    private interface Binder {
        void bind(PreparedStatement statement) throws SQLException;
    }

    @FunctionalInterface
    private interface RowMapper<T> {
        T map(ResultSet resultSet) throws SQLException;
    }

    private void inTransaction(SqlWork work) {
        try (Connection connection = dataSource.getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                work.run(connection);
                connection.commit();
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        } catch (SQLException e) {
            throw new NetworkStorageException("Network storage write failed", e);
        }
    }

    private <T> List<T> query(String sql, Binder binder, RowMapper<T> mapper) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            binder.bind(statement);
            List<T> rows = new ArrayList<>();
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    rows.add(mapper.map(resultSet));
                }
            }
            return rows;
        } catch (SQLException e) {
            throw new NetworkStorageException("Network storage read failed", e);
        }
    }
}

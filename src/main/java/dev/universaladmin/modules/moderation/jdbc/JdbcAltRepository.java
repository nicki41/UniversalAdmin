package dev.universaladmin.modules.moderation.jdbc;

import dev.universaladmin.modules.moderation.alts.AltMatch;
import dev.universaladmin.modules.moderation.alts.AltRepository;
import dev.universaladmin.scheduler.TaskScheduler;
import dev.universaladmin.storage.StorageException;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import javax.sql.DataSource;

/** {@link AltRepository} on {@code ua_player_addresses} and {@code ua_alt_key}. */
public final class JdbcAltRepository implements AltRepository {

    private static final int SECRET_BYTES = 32;

    private final DataSource dataSource;
    private final TaskScheduler scheduler;

    public JdbcAltRepository(DataSource dataSource, TaskScheduler scheduler) {
        this.dataSource = dataSource;
        this.scheduler = scheduler;
    }

    @Override
    public CompletableFuture<String> secret() {
        return scheduler.supplyAsync(() -> {
            try (Connection connection = dataSource.getConnection()) {
                String existing = readSecret(connection);
                if (existing != null) {
                    return existing;
                }
                byte[] random = new byte[SECRET_BYTES];
                new SecureRandom().nextBytes(random);
                try (PreparedStatement insert = connection.prepareStatement("INSERT INTO ua_alt_key (id, secret) VALUES (1, ?)")) {
                    insert.setString(1, HexFormat.of().formatHex(random));
                    insert.executeUpdate();
                } catch (SQLException raced) {
                    // Another server of the network created it a moment earlier - use theirs.
                }
                String created = readSecret(connection);
                if (created == null) {
                    throw new StorageException("Could not create the alt detection key", null);
                }
                return created;
            } catch (SQLException e) {
                throw new StorageException("Failed to read the alt detection key", e);
            }
        });
    }

    private static String readSecret(Connection connection) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement("SELECT secret FROM ua_alt_key WHERE id = 1");
                ResultSet result = select.executeQuery()) {
            return result.next() ? result.getString("secret") : null;
        }
    }

    @Override
    public CompletableFuture<Void> record(UUID playerId, String playerName, String addressHash, Instant now) {
        return scheduler.supplyAsync(() -> {
            try (Connection connection = dataSource.getConnection();
                    PreparedStatement statement = connection.prepareStatement(upsertSql(connection))) {
                statement.setString(1, playerId.toString());
                statement.setString(2, addressHash);
                statement.setString(3, playerName);
                statement.setLong(4, now.toEpochMilli());
                statement.setLong(5, now.toEpochMilli());
                statement.executeUpdate();
                return null;
            } catch (SQLException e) {
                throw new StorageException("Failed to record an address of " + playerId, e);
            }
        });
    }

    private static String upsertSql(Connection connection) throws SQLException {
        boolean sqlite = connection.getMetaData().getDatabaseProductName().toLowerCase().contains("sqlite");
        if (sqlite) {
            return """
                    INSERT INTO ua_player_addresses (player_id, address_hash, player_name, first_seen, last_seen)
                    VALUES (?, ?, ?, ?, ?)
                    ON CONFLICT (player_id, address_hash) DO UPDATE SET player_name = excluded.player_name,
                        last_seen = excluded.last_seen
                    """;
        }
        return """
                INSERT INTO ua_player_addresses (player_id, address_hash, player_name, first_seen, last_seen)
                VALUES (?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE player_name = VALUES(player_name), last_seen = VALUES(last_seen)
                """;
    }

    @Override
    public CompletableFuture<List<AltMatch>> findAlts(UUID playerId, Instant since, int limit) {
        return scheduler.supplyAsync(() -> {
            try (Connection connection = dataSource.getConnection();
                    PreparedStatement statement = connection.prepareStatement("""
                            SELECT other.player_id, MAX(other.player_name) AS player_name, COUNT(*) AS shared,
                                MAX(other.last_seen) AS last_seen
                            FROM ua_player_addresses mine
                            JOIN ua_player_addresses other
                                ON other.address_hash = mine.address_hash AND other.player_id <> mine.player_id
                            WHERE mine.player_id = ? AND mine.last_seen >= ? AND other.last_seen >= ?
                            GROUP BY other.player_id
                            ORDER BY last_seen DESC
                            LIMIT ?
                            """)) {
                statement.setString(1, playerId.toString());
                statement.setLong(2, since.toEpochMilli());
                statement.setLong(3, since.toEpochMilli());
                statement.setInt(4, limit);
                List<AltMatch> alts = new ArrayList<>();
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        alts.add(new AltMatch(UUID.fromString(result.getString("player_id")), result.getString("player_name"),
                                result.getInt("shared"), Instant.ofEpochMilli(result.getLong("last_seen")), false));
                    }
                }
                return alts;
            } catch (SQLException e) {
                throw new StorageException("Failed to look up possible alts of " + playerId, e);
            }
        });
    }

    @Override
    public CompletableFuture<Integer> forget(UUID playerId) {
        return delete("DELETE FROM ua_player_addresses WHERE player_id = ?", statement -> statement.setString(1, playerId.toString()));
    }

    @Override
    public CompletableFuture<Integer> forgetBefore(Instant cutoff) {
        return delete("DELETE FROM ua_player_addresses WHERE last_seen < ?", statement -> statement.setLong(1, cutoff.toEpochMilli()));
    }

    private interface Binder {
        void bind(PreparedStatement statement) throws SQLException;
    }

    private CompletableFuture<Integer> delete(String sql, Binder binder) {
        return scheduler.supplyAsync(() -> {
            try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
                binder.bind(statement);
                return statement.executeUpdate();
            } catch (SQLException e) {
                throw new StorageException("Failed to delete stored addresses", e);
            }
        });
    }
}

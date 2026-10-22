package dev.universaladmin.modules.whitelist.jdbc;

import dev.universaladmin.modules.whitelist.WhitelistEntry;
import dev.universaladmin.modules.whitelist.WhitelistEntryRepository;
import dev.universaladmin.modules.whitelist.WhitelistSource;
import dev.universaladmin.modules.whitelist.WhitelistStorageException;
import dev.universaladmin.scheduler.TaskScheduler;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import javax.sql.DataSource;

/**
 * {@code ua_whitelist} rows of one scope. The scope is asked for on every
 * call: an unnamed network server's own key changes once the proxy tells
 * its real name.
 */
public final class JdbcWhitelistEntryRepository implements WhitelistEntryRepository {

    private final DataSource dataSource;
    private final TaskScheduler scheduler;
    private final Supplier<String> scope;

    public JdbcWhitelistEntryRepository(DataSource dataSource, TaskScheduler scheduler, Supplier<String> scope) {
        this.dataSource = dataSource;
        this.scheduler = scheduler;
        this.scope = scope;
    }

    @Override
    public CompletableFuture<Optional<WhitelistEntry>> findById(UUID id) {
        String key = scope.get();
        return scheduler.supplyAsync(() -> {
            try (Connection connection = dataSource.getConnection();
                    PreparedStatement statement =
                            connection.prepareStatement("SELECT * FROM ua_whitelist WHERE scope = ? AND player_id = ?")) {
                statement.setString(1, key);
                statement.setString(2, id.toString());
                try (ResultSet resultSet = statement.executeQuery()) {
                    return resultSet.next() ? Optional.of(map(resultSet)) : Optional.empty();
                }
            } catch (SQLException e) {
                throw new WhitelistStorageException("Failed to load whitelist entry " + id, e);
            }
        });
    }

    @Override
    public CompletableFuture<List<WhitelistEntry>> findAll() {
        String key = scope.get();
        return scheduler.supplyAsync(() -> {
            try (Connection connection = dataSource.getConnection();
                    PreparedStatement statement = connection.prepareStatement("SELECT * FROM ua_whitelist WHERE scope = ?")) {
                statement.setString(1, key);
                try (ResultSet resultSet = statement.executeQuery()) {
                    List<WhitelistEntry> entries = new ArrayList<>();
                    while (resultSet.next()) {
                        entries.add(map(resultSet));
                    }
                    return entries;
                }
            } catch (SQLException e) {
                throw new WhitelistStorageException("Failed to load whitelist entries", e);
            }
        });
    }

    @Override
    public CompletableFuture<WhitelistEntry> save(WhitelistEntry entity) {
        String key = scope.get();
        return scheduler.supplyAsync(() -> {
            try (Connection connection = dataSource.getConnection();
                    PreparedStatement statement = connection.prepareStatement(upsertSql(connection))) {
                bind(statement, key, entity);
                statement.executeUpdate();
                return entity;
            } catch (SQLException e) {
                throw new WhitelistStorageException("Failed to save whitelist entry " + entity.playerId(), e);
            }
        });
    }

    private static String upsertSql(Connection connection) throws SQLException {
        boolean sqlite = connection.getMetaData().getDatabaseProductName().toLowerCase().contains("sqlite");
        if (sqlite) {
            return """
                    INSERT INTO ua_whitelist
                        (scope, player_id, player_name, source, added_by_id, added_by_name, added_at, reason, notes, expires_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (scope, player_id) DO UPDATE SET
                        player_name = excluded.player_name, source = excluded.source,
                        added_by_id = excluded.added_by_id, added_by_name = excluded.added_by_name,
                        added_at = excluded.added_at, reason = excluded.reason, notes = excluded.notes,
                        expires_at = excluded.expires_at
                    """;
        }
        return """
                INSERT INTO ua_whitelist
                    (scope, player_id, player_name, source, added_by_id, added_by_name, added_at, reason, notes, expires_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    player_name = VALUES(player_name), source = VALUES(source),
                    added_by_id = VALUES(added_by_id), added_by_name = VALUES(added_by_name),
                    added_at = VALUES(added_at), reason = VALUES(reason), notes = VALUES(notes),
                    expires_at = VALUES(expires_at)
                """;
    }

    @Override
    public CompletableFuture<Void> deleteById(UUID id) {
        String key = scope.get();
        return scheduler.supplyAsync(() -> {
            try (Connection connection = dataSource.getConnection();
                    PreparedStatement statement =
                            connection.prepareStatement("DELETE FROM ua_whitelist WHERE scope = ? AND player_id = ?")) {
                statement.setString(1, key);
                statement.setString(2, id.toString());
                statement.executeUpdate();
                return null;
            } catch (SQLException e) {
                throw new WhitelistStorageException("Failed to delete whitelist entry " + id, e);
            }
        });
    }

    @Override
    public CompletableFuture<Integer> adopt(String fromScope, Set<UUID> playerIds) {
        String key = scope.get();
        List<UUID> ids = List.copyOf(playerIds);
        return scheduler.supplyAsync(() -> {
            if (ids.isEmpty() || key.equals(fromScope)) {
                return 0;
            }
            int copied = 0;
            // Plain statements on purpose: an INSERT ... SELECT from the same
            // table with a NOT EXISTS on it is a dialect trap (MySQL).
            try (Connection connection = dataSource.getConnection();
                    PreparedStatement find = connection.prepareStatement("SELECT * FROM ua_whitelist WHERE scope = ? AND player_id = ?");
                    PreparedStatement insert = connection.prepareStatement(upsertSql(connection))) {
                for (UUID id : ids) {
                    if (row(find, key, id).isPresent()) {
                        continue;
                    }
                    Optional<WhitelistEntry> old = row(find, fromScope, id);
                    if (old.isEmpty()) {
                        continue;
                    }
                    bind(insert, key, old.get());
                    copied += insert.executeUpdate();
                }
                return copied;
            } catch (SQLException e) {
                throw new WhitelistStorageException("Failed to take over whitelist entries from " + fromScope, e);
            }
        });
    }

    private static void bind(PreparedStatement statement, String key, WhitelistEntry entity) throws SQLException {
        statement.setString(1, key);
        statement.setString(2, entity.playerId().toString());
        statement.setString(3, entity.playerName());
        statement.setString(4, entity.source().name());
        statement.setString(5, entity.addedById() != null ? entity.addedById().toString() : null);
        statement.setString(6, entity.addedByName());
        statement.setLong(7, entity.addedAt().toEpochMilli());
        statement.setString(8, entity.reason());
        statement.setString(9, entity.notes());
        if (entity.expiresAt() != null) {
            statement.setLong(10, entity.expiresAt().toEpochMilli());
        } else {
            statement.setNull(10, Types.BIGINT);
        }
    }

    private Optional<WhitelistEntry> row(PreparedStatement find, String key, UUID id) throws SQLException {
        find.setString(1, key);
        find.setString(2, id.toString());
        try (ResultSet resultSet = find.executeQuery()) {
            return resultSet.next() ? Optional.of(map(resultSet)) : Optional.empty();
        }
    }

    private WhitelistEntry map(ResultSet resultSet) throws SQLException {
        String addedById = resultSet.getString("added_by_id");
        long expiresAtMillis = resultSet.getLong("expires_at");
        Instant expiresAt = resultSet.wasNull() ? null : Instant.ofEpochMilli(expiresAtMillis);
        return new WhitelistEntry(
                UUID.fromString(resultSet.getString("player_id")),
                resultSet.getString("player_name"),
                WhitelistSource.valueOf(resultSet.getString("source")),
                addedById != null ? UUID.fromString(addedById) : null,
                resultSet.getString("added_by_name"),
                Instant.ofEpochMilli(resultSet.getLong("added_at")),
                resultSet.getString("reason"),
                resultSet.getString("notes"),
                expiresAt);
    }
}

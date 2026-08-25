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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import javax.sql.DataSource;

public final class JdbcWhitelistEntryRepository implements WhitelistEntryRepository {

    private final DataSource dataSource;
    private final TaskScheduler scheduler;

    public JdbcWhitelistEntryRepository(DataSource dataSource, TaskScheduler scheduler) {
        this.dataSource = dataSource;
        this.scheduler = scheduler;
    }

    @Override
    public CompletableFuture<Optional<WhitelistEntry>> findById(UUID id) {
        return scheduler.supplyAsync(() -> {
            try (Connection connection = dataSource.getConnection();
                    PreparedStatement statement =
                            connection.prepareStatement("SELECT * FROM whitelist_entries WHERE player_id = ?")) {
                statement.setString(1, id.toString());
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
        return scheduler.supplyAsync(() -> {
            try (Connection connection = dataSource.getConnection();
                    PreparedStatement statement = connection.prepareStatement("SELECT * FROM whitelist_entries");
                    ResultSet resultSet = statement.executeQuery()) {
                List<WhitelistEntry> entries = new ArrayList<>();
                while (resultSet.next()) {
                    entries.add(map(resultSet));
                }
                return entries;
            } catch (SQLException e) {
                throw new WhitelistStorageException("Failed to load whitelist entries", e);
            }
        });
    }

    @Override
    public CompletableFuture<WhitelistEntry> save(WhitelistEntry entity) {
        return scheduler.supplyAsync(() -> {
            try (Connection connection = dataSource.getConnection();
                    PreparedStatement statement = connection.prepareStatement(upsertSql(connection))) {
                statement.setString(1, entity.playerId().toString());
                statement.setString(2, entity.playerName());
                statement.setString(3, entity.source().name());
                statement.setString(4, entity.addedById() != null ? entity.addedById().toString() : null);
                statement.setString(5, entity.addedByName());
                statement.setLong(6, entity.addedAt().toEpochMilli());
                statement.setString(7, entity.reason());
                statement.setString(8, entity.notes());
                if (entity.expiresAt() != null) {
                    statement.setLong(9, entity.expiresAt().toEpochMilli());
                } else {
                    statement.setNull(9, Types.BIGINT);
                }
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
                    INSERT INTO whitelist_entries
                        (player_id, player_name, source, added_by_id, added_by_name, added_at, reason, notes, expires_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (player_id) DO UPDATE SET
                        player_name = excluded.player_name, source = excluded.source,
                        added_by_id = excluded.added_by_id, added_by_name = excluded.added_by_name,
                        added_at = excluded.added_at, reason = excluded.reason, notes = excluded.notes,
                        expires_at = excluded.expires_at
                    """;
        }
        return """
                INSERT INTO whitelist_entries
                    (player_id, player_name, source, added_by_id, added_by_name, added_at, reason, notes, expires_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    player_name = VALUES(player_name), source = VALUES(source),
                    added_by_id = VALUES(added_by_id), added_by_name = VALUES(added_by_name),
                    added_at = VALUES(added_at), reason = VALUES(reason), notes = VALUES(notes),
                    expires_at = VALUES(expires_at)
                """;
    }

    @Override
    public CompletableFuture<Void> deleteById(UUID id) {
        return scheduler.supplyAsync(() -> {
            try (Connection connection = dataSource.getConnection();
                    PreparedStatement statement =
                            connection.prepareStatement("DELETE FROM whitelist_entries WHERE player_id = ?")) {
                statement.setString(1, id.toString());
                statement.executeUpdate();
                return null;
            } catch (SQLException e) {
                throw new WhitelistStorageException("Failed to delete whitelist entry " + id, e);
            }
        });
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

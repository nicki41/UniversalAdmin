package dev.universaladmin.modules.moderation.jdbc;

import dev.universaladmin.modules.moderation.VanishRecord;
import dev.universaladmin.modules.moderation.VanishRepository;
import dev.universaladmin.scheduler.TaskScheduler;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import javax.sql.DataSource;

public final class JdbcVanishRepository implements VanishRepository {

    private final DataSource dataSource;
    private final TaskScheduler scheduler;

    public JdbcVanishRepository(DataSource dataSource, TaskScheduler scheduler) {
        this.dataSource = dataSource;
        this.scheduler = scheduler;
    }

    @Override
    public CompletableFuture<Optional<VanishRecord>> findById(UUID id) {
        return scheduler.supplyAsync(() -> {
            try (Connection connection = dataSource.getConnection();
                    PreparedStatement statement = connection.prepareStatement("SELECT * FROM vanish_state WHERE player_id = ?")) {
                statement.setString(1, id.toString());
                try (ResultSet resultSet = statement.executeQuery()) {
                    return resultSet.next() ? Optional.of(map(resultSet)) : Optional.empty();
                }
            } catch (SQLException e) {
                throw new VanishStorageException("Failed to load vanish state for " + id, e);
            }
        });
    }

    @Override
    public CompletableFuture<List<VanishRecord>> findAll() {
        return scheduler.supplyAsync(() -> {
            try (Connection connection = dataSource.getConnection();
                    PreparedStatement statement = connection.prepareStatement("SELECT * FROM vanish_state");
                    ResultSet resultSet = statement.executeQuery()) {
                List<VanishRecord> records = new ArrayList<>();
                while (resultSet.next()) {
                    records.add(map(resultSet));
                }
                return records;
            } catch (SQLException e) {
                throw new VanishStorageException("Failed to load vanish state", e);
            }
        });
    }

    @Override
    public CompletableFuture<VanishRecord> save(VanishRecord entity) {
        return scheduler.supplyAsync(() -> {
            try (Connection connection = dataSource.getConnection();
                    PreparedStatement statement = connection.prepareStatement(upsertSql(connection))) {
                statement.setString(1, entity.playerId().toString());
                statement.setLong(2, entity.vanishedAt().toEpochMilli());
                statement.executeUpdate();
                return entity;
            } catch (SQLException e) {
                throw new VanishStorageException("Failed to save vanish state for " + entity.playerId(), e);
            }
        });
    }

    private static String upsertSql(Connection connection) throws SQLException {
        boolean sqlite = connection.getMetaData().getDatabaseProductName().toLowerCase().contains("sqlite");
        if (sqlite) {
            return """
                    INSERT INTO vanish_state (player_id, vanished_at) VALUES (?, ?)
                    ON CONFLICT (player_id) DO UPDATE SET vanished_at = excluded.vanished_at
                    """;
        }
        return """
                INSERT INTO vanish_state (player_id, vanished_at) VALUES (?, ?)
                ON DUPLICATE KEY UPDATE vanished_at = VALUES(vanished_at)
                """;
    }

    @Override
    public CompletableFuture<Void> deleteById(UUID id) {
        return scheduler.supplyAsync(() -> {
            try (Connection connection = dataSource.getConnection();
                    PreparedStatement statement = connection.prepareStatement("DELETE FROM vanish_state WHERE player_id = ?")) {
                statement.setString(1, id.toString());
                statement.executeUpdate();
                return null;
            } catch (SQLException e) {
                throw new VanishStorageException("Failed to delete vanish state for " + id, e);
            }
        });
    }

    private VanishRecord map(ResultSet resultSet) throws SQLException {
        return new VanishRecord(UUID.fromString(resultSet.getString("player_id")), Instant.ofEpochMilli(resultSet.getLong("vanished_at")));
    }
}

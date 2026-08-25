package dev.universaladmin.modules.moderation.jdbc;

import dev.universaladmin.modules.moderation.StaffModeSnapshot;
import dev.universaladmin.modules.moderation.StaffModeSnapshotRepository;
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
import org.bukkit.GameMode;

public final class JdbcStaffModeSnapshotRepository implements StaffModeSnapshotRepository {

    private final DataSource dataSource;
    private final TaskScheduler scheduler;

    public JdbcStaffModeSnapshotRepository(DataSource dataSource, TaskScheduler scheduler) {
        this.dataSource = dataSource;
        this.scheduler = scheduler;
    }

    @Override
    public CompletableFuture<Optional<StaffModeSnapshot>> findById(UUID id) {
        return scheduler.supplyAsync(() -> {
            try (Connection connection = dataSource.getConnection();
                    PreparedStatement statement = connection.prepareStatement("SELECT * FROM staff_mode_snapshots WHERE player_id = ?")) {
                statement.setString(1, id.toString());
                try (ResultSet resultSet = statement.executeQuery()) {
                    return resultSet.next() ? Optional.of(map(resultSet)) : Optional.empty();
                }
            } catch (SQLException e) {
                throw new StaffModeStorageException("Failed to load staff-mode snapshot for " + id, e);
            }
        });
    }

    @Override
    public CompletableFuture<List<StaffModeSnapshot>> findAll() {
        return scheduler.supplyAsync(() -> {
            try (Connection connection = dataSource.getConnection();
                    PreparedStatement statement = connection.prepareStatement("SELECT * FROM staff_mode_snapshots");
                    ResultSet resultSet = statement.executeQuery()) {
                List<StaffModeSnapshot> snapshots = new ArrayList<>();
                while (resultSet.next()) {
                    snapshots.add(map(resultSet));
                }
                return snapshots;
            } catch (SQLException e) {
                throw new StaffModeStorageException("Failed to load staff-mode snapshots", e);
            }
        });
    }

    /** Always an insert in practice - {@code StaffModeService#enter} only calls this after confirming no row exists. */
    @Override
    public CompletableFuture<StaffModeSnapshot> save(StaffModeSnapshot entity) {
        return scheduler.supplyAsync(() -> {
            try (Connection connection = dataSource.getConnection();
                    PreparedStatement statement = connection.prepareStatement(upsertSql(connection))) {
                statement.setString(1, entity.playerId().toString());
                statement.setBytes(2, entity.inventoryData());
                statement.setString(3, entity.gameMode().name());
                statement.setFloat(4, entity.experience());
                statement.setInt(5, entity.level());
                statement.setBoolean(6, entity.allowFlight());
                statement.setBoolean(7, entity.flying());
                statement.setLong(8, entity.createdAt().toEpochMilli());
                statement.executeUpdate();
                return entity;
            } catch (SQLException e) {
                throw new StaffModeStorageException("Failed to save staff-mode snapshot for " + entity.playerId(), e);
            }
        });
    }

    private static String upsertSql(Connection connection) throws SQLException {
        boolean sqlite = connection.getMetaData().getDatabaseProductName().toLowerCase().contains("sqlite");
        if (sqlite) {
            return """
                    INSERT INTO staff_mode_snapshots
                        (player_id, inventory_data, gamemode, exp, level, allow_flight, flying, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (player_id) DO UPDATE SET
                        inventory_data = excluded.inventory_data, gamemode = excluded.gamemode,
                        exp = excluded.exp, level = excluded.level, allow_flight = excluded.allow_flight,
                        flying = excluded.flying, created_at = excluded.created_at
                    """;
        }
        return """
                INSERT INTO staff_mode_snapshots
                    (player_id, inventory_data, gamemode, exp, level, allow_flight, flying, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    inventory_data = VALUES(inventory_data), gamemode = VALUES(gamemode),
                    exp = VALUES(exp), level = VALUES(level), allow_flight = VALUES(allow_flight),
                    flying = VALUES(flying), created_at = VALUES(created_at)
                """;
    }

    @Override
    public CompletableFuture<Void> deleteById(UUID id) {
        return scheduler.supplyAsync(() -> {
            try (Connection connection = dataSource.getConnection();
                    PreparedStatement statement = connection.prepareStatement("DELETE FROM staff_mode_snapshots WHERE player_id = ?")) {
                statement.setString(1, id.toString());
                statement.executeUpdate();
                return null;
            } catch (SQLException e) {
                throw new StaffModeStorageException("Failed to delete staff-mode snapshot for " + id, e);
            }
        });
    }

    private StaffModeSnapshot map(ResultSet resultSet) throws SQLException {
        return new StaffModeSnapshot(
                UUID.fromString(resultSet.getString("player_id")),
                resultSet.getBytes("inventory_data"),
                GameMode.valueOf(resultSet.getString("gamemode")),
                resultSet.getFloat("exp"),
                resultSet.getInt("level"),
                resultSet.getBoolean("allow_flight"),
                resultSet.getBoolean("flying"),
                Instant.ofEpochMilli(resultSet.getLong("created_at")));
    }
}

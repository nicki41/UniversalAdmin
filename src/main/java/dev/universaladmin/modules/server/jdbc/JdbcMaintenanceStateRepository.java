package dev.universaladmin.modules.server.jdbc;

import dev.universaladmin.modules.server.MaintenanceState;
import dev.universaladmin.modules.server.MaintenanceStateRepository;
import dev.universaladmin.modules.server.MaintenanceStorageException;
import dev.universaladmin.scheduler.TaskScheduler;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import javax.sql.DataSource;

/**
 * Single-row ({@code id = 1}) persistence for {@link MaintenanceState}.
 * {@code allowed_players} is stored as a comma-separated list rather than a
 * separate table - a handful of names at most, and this project takes no
 * JSON library dependency (see docs/user/audit-log.md's note on
 * {@code MetadataJson} for the same reasoning elsewhere).
 */
public final class JdbcMaintenanceStateRepository implements MaintenanceStateRepository {

    private static final int ROW_ID = 1;

    private final DataSource dataSource;
    private final TaskScheduler scheduler;

    public JdbcMaintenanceStateRepository(DataSource dataSource, TaskScheduler scheduler) {
        this.dataSource = dataSource;
        this.scheduler = scheduler;
    }

    @Override
    public CompletableFuture<Optional<MaintenanceState>> load() {
        return scheduler.supplyAsync(() -> {
            try (Connection connection = dataSource.getConnection();
                    PreparedStatement statement =
                            connection.prepareStatement("SELECT * FROM server_maintenance_state WHERE id = ?")) {
                statement.setInt(1, ROW_ID);
                try (ResultSet resultSet = statement.executeQuery()) {
                    return resultSet.next() ? Optional.of(map(resultSet)) : Optional.<MaintenanceState>empty();
                }
            } catch (SQLException e) {
                throw new MaintenanceStorageException("Failed to load maintenance state", e);
            }
        });
    }

    @Override
    public CompletableFuture<Void> save(MaintenanceState state) {
        return scheduler.supplyAsync(() -> {
            try (Connection connection = dataSource.getConnection();
                    PreparedStatement statement = connection.prepareStatement(upsertSql(connection))) {
                statement.setInt(1, ROW_ID);
                statement.setBoolean(2, state.enabled());
                statement.setString(3, state.reason());
                statement.setString(4, state.message());
                statement.setString(5, String.join(",", state.allowedPlayers()));
                statement.setLong(6, state.updatedAt().toEpochMilli());
                statement.setString(7, state.updatedBy());
                statement.executeUpdate();
                return null;
            } catch (SQLException e) {
                throw new MaintenanceStorageException("Failed to save maintenance state", e);
            }
        });
    }

    private static String upsertSql(Connection connection) throws SQLException {
        boolean sqlite = connection.getMetaData().getDatabaseProductName().toLowerCase().contains("sqlite");
        if (sqlite) {
            return """
                    INSERT INTO server_maintenance_state (id, enabled, reason, message, allowed_players, updated_at, updated_by)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (id) DO UPDATE SET enabled = excluded.enabled, reason = excluded.reason,
                        message = excluded.message, allowed_players = excluded.allowed_players,
                        updated_at = excluded.updated_at, updated_by = excluded.updated_by
                    """;
        }
        return """
                INSERT INTO server_maintenance_state (id, enabled, reason, message, allowed_players, updated_at, updated_by)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE enabled = VALUES(enabled), reason = VALUES(reason),
                    message = VALUES(message), allowed_players = VALUES(allowed_players),
                    updated_at = VALUES(updated_at), updated_by = VALUES(updated_by)
                """;
    }

    private MaintenanceState map(ResultSet resultSet) throws SQLException {
        String allowedPlayersRaw = resultSet.getString("allowed_players");
        Set<String> allowedPlayers = allowedPlayersRaw == null || allowedPlayersRaw.isBlank()
                ? Set.of()
                : Arrays.stream(allowedPlayersRaw.split(",")).filter(name -> !name.isBlank()).collect(Collectors.toSet());
        return new MaintenanceState(
                resultSet.getBoolean("enabled"),
                resultSet.getString("reason"),
                resultSet.getString("message"),
                allowedPlayers,
                Instant.ofEpochMilli(resultSet.getLong("updated_at")),
                resultSet.getString("updated_by"));
    }
}

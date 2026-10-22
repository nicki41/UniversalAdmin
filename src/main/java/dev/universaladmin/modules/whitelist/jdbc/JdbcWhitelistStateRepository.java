package dev.universaladmin.modules.whitelist.jdbc;

import dev.universaladmin.modules.whitelist.WhitelistStateRepository;
import dev.universaladmin.modules.whitelist.WhitelistStorageException;
import dev.universaladmin.scheduler.TaskScheduler;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import javax.sql.DataSource;

/** {@code ua_whitelist_state}: one row per scope. UniversalAdmin Velocity reads the {@code network} row. */
public final class JdbcWhitelistStateRepository implements WhitelistStateRepository {

    private final DataSource dataSource;
    private final TaskScheduler scheduler;

    public JdbcWhitelistStateRepository(DataSource dataSource, TaskScheduler scheduler) {
        this.dataSource = dataSource;
        this.scheduler = scheduler;
    }

    @Override
    public CompletableFuture<Optional<Boolean>> load(String scope) {
        return scheduler.supplyAsync(() -> {
            try (Connection connection = dataSource.getConnection();
                    PreparedStatement statement = connection.prepareStatement("SELECT enabled FROM ua_whitelist_state WHERE scope = ?")) {
                statement.setString(1, scope);
                try (ResultSet resultSet = statement.executeQuery()) {
                    return resultSet.next() ? Optional.of(resultSet.getBoolean("enabled")) : Optional.<Boolean>empty();
                }
            } catch (SQLException e) {
                throw new WhitelistStorageException("Failed to load the whitelist state of " + scope, e);
            }
        });
    }

    @Override
    public CompletableFuture<Void> save(String scope, boolean enabled, String updatedBy) {
        return scheduler.supplyAsync(() -> {
            try (Connection connection = dataSource.getConnection();
                    PreparedStatement statement = connection.prepareStatement(upsertSql(connection))) {
                statement.setString(1, scope);
                statement.setBoolean(2, enabled);
                statement.setLong(3, System.currentTimeMillis());
                statement.setString(4, updatedBy);
                statement.executeUpdate();
                return null;
            } catch (SQLException e) {
                throw new WhitelistStorageException("Failed to save the whitelist state of " + scope, e);
            }
        });
    }

    private static String upsertSql(Connection connection) throws SQLException {
        boolean sqlite = connection.getMetaData().getDatabaseProductName().toLowerCase().contains("sqlite");
        if (sqlite) {
            return """
                    INSERT INTO ua_whitelist_state (scope, enabled, updated_at, updated_by) VALUES (?, ?, ?, ?)
                    ON CONFLICT (scope) DO UPDATE SET enabled = excluded.enabled, updated_at = excluded.updated_at,
                        updated_by = excluded.updated_by
                    """;
        }
        return """
                INSERT INTO ua_whitelist_state (scope, enabled, updated_at, updated_by) VALUES (?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE enabled = VALUES(enabled), updated_at = VALUES(updated_at), updated_by = VALUES(updated_by)
                """;
    }
}

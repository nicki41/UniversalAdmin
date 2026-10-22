package dev.universaladmin.modules.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.universaladmin.modules.server.jdbc.JdbcMaintenanceStateRepository;
import dev.universaladmin.scheduler.TaskScheduler;
import dev.universaladmin.storage.DatabaseConfig;
import dev.universaladmin.storage.MigrationRunner;
import dev.universaladmin.storage.jdbc.DataSourceFactory;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.logging.Logger;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Runs {@link MaintenanceScopeMigration} against a real temporary SQLite
 * database (see {@code MigrationRunnerTest}'s precedent) and reads the result
 * through {@link JdbcMaintenanceStateRepository}.
 */
class MaintenanceScopeMigrationTest {

    private static final TaskScheduler DIRECT = new TaskScheduler() {
        @Override
        public <T> CompletableFuture<T> supplyAsync(Supplier<T> task) {
            return CompletableFuture.completedFuture(task.get());
        }

        @Override
        public CompletableFuture<Void> runAsync(Runnable task) {
            task.run();
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void runOnMainThread(Runnable task) {
            task.run();
        }

        @Override
        public void close() {
        }
    };

    @Test
    void copiesTheSingleSharedRowToTheLocalScope(@TempDir Path dir) throws Exception {
        DataSource dataSource = DataSourceFactory.create(DatabaseConfig.sqlite("test.db"), dir);
        try {
            MigrationRunner before = new MigrationRunner(dataSource, Logger.getLogger("test"));
            before.register(new ServerMaintenanceMigration());
            before.runPending();
            try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
                statement.executeUpdate("INSERT INTO server_maintenance_state (id, enabled, reason, message, allowed_players, "
                        + "updated_at, updated_by) VALUES (1, 1, 'new world', '<red>Closed', 'alice,bob', 123, 'tester')");
            }

            MigrationRunner after = new MigrationRunner(dataSource, Logger.getLogger("test"));
            after.register(new ServerMaintenanceMigration());
            after.register(new MaintenanceScopeMigration());
            after.runPending();

            JdbcMaintenanceStateRepository repository = new JdbcMaintenanceStateRepository(dataSource, DIRECT);
            Map<String, MaintenanceState> rows = repository.load(Set.of("local", "network", "server:lobby")).join();
            assertEquals(Set.of("local"), rows.keySet());
            MaintenanceState local = rows.get("local");
            assertTrue(local.enabled());
            assertEquals("new world", local.reason());
            assertEquals("<red>Closed", local.message());
            assertEquals(Set.of("alice", "bob"), local.allowedPlayers());
        } finally {
            ((AutoCloseable) dataSource).close();
        }
    }

    @Test
    void keepsEveryScopeApart(@TempDir Path dir) throws Exception {
        DataSource dataSource = DataSourceFactory.create(DatabaseConfig.sqlite("test.db"), dir);
        try {
            MigrationRunner runner = new MigrationRunner(dataSource, Logger.getLogger("test"));
            runner.register(new ServerMaintenanceMigration());
            runner.register(new MaintenanceScopeMigration());
            runner.runPending();
            JdbcMaintenanceStateRepository repository = new JdbcMaintenanceStateRepository(dataSource, DIRECT);

            repository.save("server:lobby", new MaintenanceState(true, "lobby", null, Set.of(), Instant.ofEpochMilli(1), "a")).join();
            repository.save("network", new MaintenanceState(false, null, null, Set.of("alice"), Instant.ofEpochMilli(2), "b")).join();
            repository.save("server:lobby", new MaintenanceState(false, "lobby", null, Set.of(), Instant.ofEpochMilli(3), "c")).join();

            Map<String, MaintenanceState> rows = repository.load(Set.of("server:lobby", "server:survival", "network")).join();
            assertEquals(Set.of("server:lobby", "network"), rows.keySet());
            assertFalse(rows.get("server:lobby").enabled());
            assertEquals("c", rows.get("server:lobby").updatedBy());
            assertEquals(Set.of("alice"), rows.get("network").allowedPlayers());
        } finally {
            ((AutoCloseable) dataSource).close();
        }
    }
}

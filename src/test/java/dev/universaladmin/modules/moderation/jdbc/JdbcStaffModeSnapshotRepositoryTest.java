package dev.universaladmin.modules.moderation.jdbc;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.universaladmin.modules.moderation.StaffModeSnapshot;
import dev.universaladmin.modules.moderation.StaffModeSnapshotMigration;
import dev.universaladmin.scheduler.TaskScheduler;
import dev.universaladmin.storage.DatabaseConfig;
import dev.universaladmin.storage.MigrationRunner;
import dev.universaladmin.storage.jdbc.DataSourceFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.logging.Logger;
import javax.sql.DataSource;
import org.bukkit.GameMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Round-trips a real BLOB column through a real temp SQLite DB with a
 * <b>synthetic</b> byte[] payload, not real {@code ItemStack.serializeAsBytes()}
 * output - that call needs a live Paper server (same documented exclusion
 * {@code PlayerServiceTest} uses for {@code snapshot()}), so this test
 * proves the persistence layer (migration/dialect/column) works correctly,
 * independent of Bukkit item serialization itself.
 */
class JdbcStaffModeSnapshotRepositoryTest {

    private static final TaskScheduler IMMEDIATE = new TaskScheduler() {
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
    void savesFindsAndDeletesASnapshot(@TempDir Path dir) throws Exception {
        DataSource dataSource = DataSourceFactory.create(DatabaseConfig.sqlite("staff-mode.db"), dir);
        try {
            MigrationRunner migrations = new MigrationRunner(dataSource, Logger.getLogger("test"));
            migrations.register(new StaffModeSnapshotMigration());
            migrations.runPending();

            JdbcStaffModeSnapshotRepository repository = new JdbcStaffModeSnapshotRepository(dataSource, IMMEDIATE);
            UUID playerId = UUID.randomUUID();
            byte[] syntheticInventoryData = "not-real-itemstack-bytes, just proving the BLOB round-trip".getBytes(StandardCharsets.UTF_8);
            Instant createdAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
            StaffModeSnapshot snapshot = new StaffModeSnapshot(
                    playerId, syntheticInventoryData, GameMode.SURVIVAL, 0.5f, 12, true, false, createdAt);

            repository.save(snapshot).join();

            Optional<StaffModeSnapshot> loaded = repository.findById(playerId).join();
            assertTrue(loaded.isPresent());
            assertArrayEquals(syntheticInventoryData, loaded.get().inventoryData());
            assertEquals(GameMode.SURVIVAL, loaded.get().gameMode());
            assertEquals(12, loaded.get().level());
            assertTrue(loaded.get().allowFlight());
            assertFalse(loaded.get().flying());
            assertEquals(createdAt, loaded.get().createdAt());

            repository.deleteById(playerId).join();
            assertTrue(repository.findById(playerId).join().isEmpty());
        } finally {
            ((AutoCloseable) dataSource).close();
        }
    }

    @Test
    void savingTwiceForTheSamePlayerUpdatesRatherThanDuplicates(@TempDir Path dir) throws Exception {
        DataSource dataSource = DataSourceFactory.create(DatabaseConfig.sqlite("staff-mode-upsert.db"), dir);
        try {
            MigrationRunner migrations = new MigrationRunner(dataSource, Logger.getLogger("test"));
            migrations.register(new StaffModeSnapshotMigration());
            migrations.runPending();

            JdbcStaffModeSnapshotRepository repository = new JdbcStaffModeSnapshotRepository(dataSource, IMMEDIATE);
            UUID playerId = UUID.randomUUID();
            Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
            repository.save(new StaffModeSnapshot(playerId, "first".getBytes(StandardCharsets.UTF_8), GameMode.SURVIVAL, 0f, 0, false, false, now)).join();
            repository.save(new StaffModeSnapshot(playerId, "second".getBytes(StandardCharsets.UTF_8), GameMode.CREATIVE, 0f, 0, true, true, now)).join();

            assertEquals(1, repository.findAll().join().size());
            StaffModeSnapshot loaded = repository.findById(playerId).join().orElseThrow();
            assertArrayEquals("second".getBytes(StandardCharsets.UTF_8), loaded.inventoryData());
            assertEquals(GameMode.CREATIVE, loaded.gameMode());
        } finally {
            ((AutoCloseable) dataSource).close();
        }
    }
}

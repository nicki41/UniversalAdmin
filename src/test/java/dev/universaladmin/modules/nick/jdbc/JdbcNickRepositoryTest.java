package dev.universaladmin.modules.nick.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.universaladmin.modules.nick.NickMigration;
import dev.universaladmin.modules.nick.NickNameTakenException;
import dev.universaladmin.modules.nick.NickRecord;
import dev.universaladmin.scheduler.TaskScheduler;
import dev.universaladmin.storage.DatabaseConfig;
import dev.universaladmin.storage.MigrationRunner;
import dev.universaladmin.storage.jdbc.DataSourceFactory;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;
import java.util.logging.Logger;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** A real SQLite database: one nick per player, and no name twice. */
class JdbcNickRepositoryTest {

    private static final TaskScheduler IMMEDIATE = new TaskScheduler() {
        @Override
        public <T> CompletableFuture<T> supplyAsync(Supplier<T> task) {
            try {
                return CompletableFuture.completedFuture(task.get());
            } catch (RuntimeException e) {
                return CompletableFuture.failedFuture(e);
            }
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
    void storesReplacesAndFindsNicksByName(@TempDir Path dir) throws Exception {
        DataSource dataSource = DataSourceFactory.create(DatabaseConfig.sqlite("nicks.db"), dir);
        try {
            MigrationRunner migrations = new MigrationRunner(dataSource, Logger.getLogger("test"));
            migrations.register(new NickMigration());
            migrations.runPending();
            JdbcNickRepository repository = new JdbcNickRepository(dataSource, IMMEDIATE);
            UUID admin = UUID.randomUUID();
            Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
            NickRecord.Skin skin = new NickRecord.Skin("Notch", "dGV4dHVyZXM=", "c2lnbmF0dXJl");

            repository.save(new NickRecord(admin, "Admin", "Notch", skin, now)).join();
            NickRecord stored = repository.findByName("notch").join().orElseThrow();
            assertEquals(admin, stored.playerId());
            assertEquals("Admin", stored.realName());
            assertEquals(skin, stored.skin());
            assertEquals(now, stored.createdAt());

            // A second nick replaces the first - the old name is free again.
            repository.save(new NickRecord(admin, "Admin", "Bob", null, now)).join();
            assertTrue(repository.findByName("Notch").join().isEmpty());
            NickRecord replaced = repository.findById(admin).join().orElseThrow();
            assertEquals("Bob", replaced.name());
            assertNull(replaced.skin());
            assertEquals(1, repository.findAll().join().size());

            UUID moderator = UUID.randomUUID();
            CompletionException taken = assertThrows(CompletionException.class,
                    () -> repository.save(new NickRecord(moderator, "Moderator", "BOB", null, now)).join());
            assertInstanceOf(NickNameTakenException.class, taken.getCause());

            repository.deleteById(admin).join();
            assertTrue(repository.findById(admin).join().isEmpty());
            repository.save(new NickRecord(moderator, "Moderator", "BOB", null, now)).join();
            assertEquals(moderator, repository.findByName("bob").join().orElseThrow().playerId());
        } finally {
            ((AutoCloseable) dataSource).close();
        }
    }
}

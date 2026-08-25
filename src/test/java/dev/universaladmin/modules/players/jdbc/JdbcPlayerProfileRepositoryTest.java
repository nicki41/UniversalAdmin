package dev.universaladmin.modules.players.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.universaladmin.modules.players.PlayerProfile;
import dev.universaladmin.modules.players.PlayerProfileIndexMigration;
import dev.universaladmin.modules.players.PlayerProfileMigration;
import dev.universaladmin.modules.players.PlayerSearchQuery;
import dev.universaladmin.modules.players.PlayerSort;
import dev.universaladmin.scheduler.TaskScheduler;
import dev.universaladmin.storage.DatabaseConfig;
import dev.universaladmin.storage.MigrationRunner;
import dev.universaladmin.storage.jdbc.DataSourceFactory;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.logging.Logger;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Exercises the storage foundation end-to-end - a real SQLite file, a real
 * {@link PlayerProfileMigration}, a real JDBC repository - as the baseline
 * pattern other repositories are modeled on. See docs/development/testing.md
 * and docs/architecture/storage.md.
 */
class JdbcPlayerProfileRepositoryTest {

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
    void savesFindsUpsertsAndDeletesAProfile(@TempDir Path dir) throws Exception {
        DataSource dataSource = DataSourceFactory.create(DatabaseConfig.sqlite("players.db"), dir);
        try {
            MigrationRunner migrations = new MigrationRunner(dataSource, Logger.getLogger("test"));
            migrations.register(new PlayerProfileMigration());
            migrations.runPending();

            JdbcPlayerProfileRepository repository = new JdbcPlayerProfileRepository(dataSource, IMMEDIATE);
            UUID playerId = UUID.randomUUID();
            // Persisted as epoch millis (see JdbcPlayerProfileRepository.map),
            // so compare against a millis-truncated Instant.
            Instant firstJoin = Instant.now().minusSeconds(60).truncatedTo(ChronoUnit.MILLIS);
            PlayerProfile profile = new PlayerProfile(playerId, "Notch", firstJoin, firstJoin);

            repository.save(profile).join();
            Optional<PlayerProfile> loaded = repository.findById(playerId).join();
            assertTrue(loaded.isPresent());
            assertEquals("Notch", loaded.get().lastKnownName());

            // save() upserts by id - exercises the SQLite ON CONFLICT branch
            // in JdbcPlayerProfileRepository.upsertSql, not a second row.
            repository.save(profile.withLastSeen("Notch2", Instant.now())).join();
            List<PlayerProfile> all = repository.findAll().join();
            assertEquals(1, all.size());
            assertEquals("Notch2", all.get(0).lastKnownName());
            assertEquals(firstJoin, all.get(0).firstJoin());

            repository.deleteById(playerId).join();
            assertTrue(repository.findById(playerId).join().isEmpty());
        } finally {
            ((AutoCloseable) dataSource).close();
        }
    }

    @Test
    void searchFiltersByNameSortsAndLimits(@TempDir Path dir) throws Exception {
        DataSource dataSource = DataSourceFactory.create(DatabaseConfig.sqlite("players-search.db"), dir);
        try {
            MigrationRunner migrations = new MigrationRunner(dataSource, Logger.getLogger("test"));
            migrations.register(new PlayerProfileMigration());
            migrations.register(new PlayerProfileIndexMigration());
            migrations.runPending();

            JdbcPlayerProfileRepository repository = new JdbcPlayerProfileRepository(dataSource, IMMEDIATE);
            Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
            save(repository, "Alice", now.minusSeconds(300), now.minusSeconds(300));
            save(repository, "Bob", now.minusSeconds(200), now.minusSeconds(100));
            save(repository, "Alicia", now.minusSeconds(100), now.minusSeconds(50));

            // Case-insensitive substring match.
            List<PlayerProfile> aliMatches =
                    repository.search(new PlayerSearchQuery("ali", PlayerSort.NAME_ASC, 10)).join();
            assertEquals(2, aliMatches.size());
            assertEquals("Alice", aliMatches.get(0).lastKnownName());
            assertEquals("Alicia", aliMatches.get(1).lastKnownName());

            // No filter, sorted by last-seen descending, limit applied.
            List<PlayerProfile> recent =
                    repository.search(new PlayerSearchQuery(null, PlayerSort.LAST_SEEN_DESC, 2)).join();
            assertEquals(2, recent.size());
            assertEquals("Alicia", recent.get(0).lastKnownName());
            assertEquals("Bob", recent.get(1).lastKnownName());
        } finally {
            ((AutoCloseable) dataSource).close();
        }
    }

    private void save(JdbcPlayerProfileRepository repository, String name, Instant firstJoin, Instant lastSeen) {
        repository.save(new PlayerProfile(UUID.randomUUID(), name, firstJoin, lastSeen)).join();
    }
}

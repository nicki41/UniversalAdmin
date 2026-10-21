package dev.universaladmin.modules.moderation.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.universaladmin.modules.moderation.ModerationPunishmentIndexMigration;
import dev.universaladmin.modules.moderation.ModerationPunishmentMigration;
import dev.universaladmin.modules.moderation.Punishment;
import dev.universaladmin.modules.moderation.PunishmentType;
import dev.universaladmin.modules.moderation.importing.ImportRecordsMigration;
import dev.universaladmin.modules.moderation.importing.ImportedNote;
import dev.universaladmin.modules.moderation.importing.ImportedPunishment;
import dev.universaladmin.modules.moderation.notes.PlayerNote;
import dev.universaladmin.modules.moderation.notes.PlayerNotesMigration;
import dev.universaladmin.modules.players.PlayerProfile;
import dev.universaladmin.modules.players.PlayerProfileMigration;
import dev.universaladmin.modules.players.jdbc.JdbcPlayerProfileRepository;
import dev.universaladmin.scheduler.TaskScheduler;
import dev.universaladmin.storage.DatabaseConfig;
import dev.universaladmin.storage.MigrationRunner;
import dev.universaladmin.storage.jdbc.DataSourceFactory;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.logging.Logger;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** A real SQLite database: imported rows land in the normal tables and are remembered per source. */
class JdbcImportRepositoryTest {

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
    void writesPunishmentsNotesAndImportRecords(@TempDir Path dir) throws Exception {
        DataSource dataSource = DataSourceFactory.create(DatabaseConfig.sqlite("import.db"), dir);
        try {
            MigrationRunner migrations = new MigrationRunner(dataSource, Logger.getLogger("test"));
            migrations.register(new ModerationPunishmentMigration());
            migrations.register(new ModerationPunishmentIndexMigration());
            migrations.register(new PlayerNotesMigration());
            migrations.register(new ImportRecordsMigration());
            migrations.register(new PlayerProfileMigration());
            migrations.runPending();

            JdbcImportRepository repository = new JdbcImportRepository(dataSource, IMMEDIATE);
            UUID steve = UUID.randomUUID();
            Instant created = Instant.now().minus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MILLIS);
            ImportedPunishment ban = new ImportedPunishment("ban:7", PunishmentType.BAN, steve, "Steve", null, null, "Admin",
                    "griefing", created, Instant.now().plus(1, ChronoUnit.DAYS), true, null, null, "lobby");
            ImportedNote note = new ImportedNote("jail:1", steve, "Steve", "EssentialsX", "Was in jail", created);

            assertEquals(2, repository.write("litebans", List.of(ban), List.of(note)).join());

            assertEquals(Set.of("ban:7", "jail:1"), repository.importedIds("litebans").join());
            assertTrue(repository.importedIds("advancedban").join().isEmpty());

            JdbcPunishmentRepository punishments = new JdbcPunishmentRepository(dataSource, IMMEDIATE);
            Punishment stored = punishments.findActiveBan(steve, Instant.now()).join().orElseThrow();
            assertEquals(PunishmentType.TEMP_BAN, stored.type());
            assertEquals("griefing", stored.reason());
            assertEquals(created, stored.createdAt());
            assertEquals("litebans", stored.metadata().get("imported_from"));
            assertEquals("ban:7", stored.metadata().get("import_id"));
            assertEquals("lobby", stored.metadata().get("server"));

            List<PlayerNote> notes = new JdbcPlayerNoteRepository(dataSource, IMMEDIATE).findByTarget(steve, 10).join();
            assertEquals(1, notes.size());
            assertEquals("Was in jail", notes.get(0).text());

            // Imported players become known without touching existing profiles.
            JdbcPlayerProfileRepository profiles = new JdbcPlayerProfileRepository(dataSource, IMMEDIATE);
            UUID alex = UUID.randomUUID();
            profiles.save(new PlayerProfile(alex, "Alex", created, Instant.now())).join();
            int added = profiles.insertIfAbsent(List.of(
                    new PlayerProfile(steve, "Steve", created, created),
                    new PlayerProfile(alex, "OldAlex", created, created))).join();
            assertEquals(1, added);
            assertEquals("Alex", profiles.findById(alex).join().orElseThrow().lastKnownName());
            assertEquals("Steve", profiles.findById(steve).join().orElseThrow().lastKnownName());
        } finally {
            ((AutoCloseable) dataSource).close();
        }
    }
}

package dev.universaladmin.audit.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.universaladmin.action.ActionTarget;
import dev.universaladmin.action.Actor;
import dev.universaladmin.action.Source;
import dev.universaladmin.audit.AuditEvent;
import dev.universaladmin.audit.AuditEventType;
import dev.universaladmin.audit.AuditPage;
import dev.universaladmin.audit.AuditPosition;
import dev.universaladmin.audit.AuditQuery;
import dev.universaladmin.audit.AuditSchemaMigration;
import dev.universaladmin.audit.AuditSchemaMigrationV2;
import dev.universaladmin.permission.PermissionEvaluator;
import dev.universaladmin.scheduler.TaskScheduler;
import dev.universaladmin.storage.DatabaseConfig;
import dev.universaladmin.storage.MigrationRunner;
import dev.universaladmin.storage.jdbc.DataSourceFactory;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.logging.Logger;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Exercises {@link JdbcAuditEventRepository} against a real (temporary)
 * SQLite database, including both {@link AuditSchemaMigration} and
 * {@link AuditSchemaMigrationV2} - see docs/development/testing.md and
 * docs/architecture/storage.md.
 */
class JdbcAuditEventRepositoryTest {

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

    private final List<DataSource> openDataSources = new ArrayList<>();

    @AfterEach
    void closeDataSources() throws Exception {
        for (DataSource dataSource : openDataSources) {
            ((AutoCloseable) dataSource).close();
        }
    }

    @Test
    void persistsAndReloadsEveryFieldIncludingMetadataAndPosition(@TempDir Path dir) throws Exception {
        JdbcAuditEventRepository repository = repository(dir, "persist.db");
        UUID actorId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        AuditEvent entry = AuditEvent.builder(AuditEventType.core("players.kick"), player(actorId), Source.COMMAND, "Kicked Alex")
                .module("moderation")
                .target(ActionTarget.player(targetId, "Alex"))
                .success(true)
                .reason("Spamming")
                .oldValue("SURVIVAL")
                .newValue("null")
                .world("world")
                .position(new AuditPosition(12.5, 64.0, -30.25))
                .metadata(Map.of("durationSeconds", 300L, "silent", true, "note", "second offense"))
                .correlationId("req-42")
                .build();

        AuditEvent saved = repository.save(entry).join();
        assertNull(entry.id());
        assertTrue(saved.id() != null && saved.id() > 0);

        AuditEvent loaded = repository.findById(saved.id()).join().orElseThrow();
        assertEquals(actorId, loaded.actor().playerId());
        assertEquals("Steve", loaded.actor().displayName());
        assertEquals(AuditEventType.core("players.kick"), loaded.type());
        assertEquals("moderation", loaded.module());
        assertEquals(targetId.toString(), loaded.target().id());
        assertEquals("Alex", loaded.target().displayName());
        assertEquals(Source.COMMAND, loaded.source());
        assertTrue(loaded.success());
        assertEquals("Spamming", loaded.reason());
        assertEquals("SURVIVAL", loaded.oldValue());
        assertEquals("world", loaded.world());
        assertEquals(12.5, loaded.position().x());
        assertEquals(-30.25, loaded.position().z());
        assertEquals(300L, loaded.metadata().get("durationSeconds"));
        assertEquals(true, loaded.metadata().get("silent"));
        assertEquals("second offense", loaded.metadata().get("note"));
        assertEquals("req-42", loaded.correlationId());
    }

    @Test
    void persistsAnEntryWithNoOptionalFields(@TempDir Path dir) throws Exception {
        JdbcAuditEventRepository repository = repository(dir, "minimal.db");
        AuditEvent entry = AuditEvent.builder(AuditEventType.core("config.reload"), Actor.console(), Source.COMMAND, "Reloaded config").build();

        AuditEvent saved = repository.save(entry).join();
        AuditEvent loaded = repository.findById(saved.id()).join().orElseThrow();

        assertNull(loaded.module());
        assertNull(loaded.target());
        assertNull(loaded.position());
        assertTrue(loaded.metadata().isEmpty());
        assertNull(loaded.correlationId());
    }

    @Test
    void queryFiltersByModuleSourceAndSuccess(@TempDir Path dir) throws Exception {
        JdbcAuditEventRepository repository = repository(dir, "filters.db");
        repository.save(entry("players", Source.COMMAND, true)).join();
        repository.save(entry("players", Source.GUI, false)).join();
        repository.save(entry("moderation", Source.COMMAND, true)).join();

        AuditPage byModule = repository.query(AuditQuery.builder().module("players").build()).join();
        assertEquals(2, byModule.totalCount());

        AuditPage byModuleAndSource = repository.query(
                AuditQuery.builder().module("players").source(Source.GUI).build()).join();
        assertEquals(1, byModuleAndSource.totalCount());

        AuditPage onlyFailures = repository.query(AuditQuery.builder().success(false).build()).join();
        assertEquals(1, onlyFailures.totalCount());
        assertEquals(Source.GUI, onlyFailures.items().get(0).source());
    }

    @Test
    void queryFiltersByActorTargetAndTimeRange(@TempDir Path dir) throws Exception {
        JdbcAuditEventRepository repository = repository(dir, "actor-target.db");
        UUID actorId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        repository.save(AuditEvent.builder(AuditEventType.core("players.kick"), player(actorId), Source.COMMAND, "x")
                .target(ActionTarget.player(targetId, "Alex")).build()).join();
        repository.save(AuditEvent.builder(AuditEventType.core("players.kick"), player(UUID.randomUUID()), Source.COMMAND, "y")
                .target(ActionTarget.player(UUID.randomUUID(), "Bob")).build()).join();

        assertEquals(1, repository.query(AuditQuery.builder().actorId(actorId).build()).join().totalCount());
        assertEquals(1, repository.query(AuditQuery.builder().targetId(targetId.toString()).build()).join().totalCount());

        Instant future = Instant.now().plusSeconds(3600);
        assertEquals(0, repository.query(AuditQuery.builder().timeRange(future, null).build()).join().totalCount());
        assertEquals(2, repository.query(AuditQuery.builder().timeRange(null, future).build()).join().totalCount());
    }

    @Test
    void queryPaginatesNewestFirst(@TempDir Path dir) throws Exception {
        JdbcAuditEventRepository repository = repository(dir, "pagination.db");
        for (int i = 0; i < 5; i++) {
            repository.save(entry("players", Source.COMMAND, true)).join();
        }

        AuditPage firstPage = repository.query(AuditQuery.builder().pageSize(2).page(0).build()).join();
        assertEquals(5, firstPage.totalCount());
        assertEquals(2, firstPage.items().size());
        assertEquals(3, firstPage.totalPages());
        assertTrue(firstPage.hasNext());
        assertTrue(!firstPage.hasPrevious());

        AuditPage lastPage = repository.query(AuditQuery.builder().pageSize(2).page(2).build()).join();
        assertEquals(1, lastPage.items().size());
        assertTrue(!lastPage.hasNext());
        assertTrue(lastPage.hasPrevious());
    }

    @Test
    void deleteOlderThanRemovesOnlyExpiredEntries(@TempDir Path dir) throws Exception {
        JdbcAuditEventRepository repository = repository(dir, "retention.db");
        Instant cutoff = Instant.now();
        // Insert directly with a controlled occurred_at instead of relying on
        // AuditEvent.builder()'s Instant.now(), so "older" vs "newer" than the
        // cutoff is deterministic regardless of test execution speed.
        insertAt(repository, cutoff.minus(10, ChronoUnit.DAYS));
        insertAt(repository, cutoff.plus(10, ChronoUnit.DAYS));

        int deleted = repository.deleteOlderThan(cutoff).join();

        assertEquals(1, deleted);
        assertEquals(1, repository.recent(10).join().size());
    }

    private void insertAt(JdbcAuditEventRepository repository, Instant timestamp) {
        AuditEvent entry = entry("players", Source.COMMAND, true);
        AuditEvent withTimestamp = new AuditEvent(
                null, timestamp, entry.actor(), entry.type(), entry.module(), entry.target(), entry.source(),
                entry.success(), entry.reason(), entry.oldValue(), entry.newValue(), entry.world(), entry.position(),
                entry.summary(), entry.metadata(), entry.correlationId());
        repository.save(withTimestamp).join();
    }

    private AuditEvent entry(String module, Source source, boolean success) {
        return AuditEvent.builder(AuditEventType.core("players.kick"), Actor.console(), source, "test entry")
                .module(module)
                .success(success)
                .build();
    }

    private Actor player(UUID id) {
        return Actor.player(id, "Steve", PermissionEvaluator.denyAll());
    }

    private JdbcAuditEventRepository repository(Path dir, String fileName) {
        DataSource dataSource = DataSourceFactory.create(DatabaseConfig.sqlite(fileName), dir);
        openDataSources.add(dataSource);
        MigrationRunner migrations = new MigrationRunner(dataSource, Logger.getLogger("test"));
        migrations.register(new AuditSchemaMigration());
        migrations.register(new AuditSchemaMigrationV2());
        migrations.runPending();
        return new JdbcAuditEventRepository(dataSource, IMMEDIATE);
    }
}

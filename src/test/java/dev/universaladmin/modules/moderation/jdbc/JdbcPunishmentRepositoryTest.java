package dev.universaladmin.modules.moderation.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.universaladmin.modules.moderation.ModerationPunishmentIndexMigration;
import dev.universaladmin.modules.moderation.ModerationPunishmentMigration;
import dev.universaladmin.modules.moderation.MuteKindMigration;
import dev.universaladmin.modules.moderation.Punishment;
import dev.universaladmin.modules.moderation.PunishmentQuery;
import dev.universaladmin.modules.moderation.PunishmentTemplateMigration;
import dev.universaladmin.modules.moderation.PunishmentType;
import dev.universaladmin.scheduler.TaskScheduler;
import dev.universaladmin.status.MuteKind;
import dev.universaladmin.storage.DatabaseConfig;
import dev.universaladmin.storage.MigrationRunner;
import dev.universaladmin.storage.jdbc.DataSourceFactory;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Exercises the storage foundation end-to-end - a real SQLite file, real
 * migrations, a real JDBC repository - same baseline pattern
 * {@code JdbcPlayerProfileRepositoryTest} established.
 */
class JdbcPunishmentRepositoryTest {

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
    void savesFindsAndUpdatesAPunishment(@TempDir Path dir) throws Exception {
        DataSource dataSource = openDatabase(dir, "punishments.db");
        try {
            JdbcPunishmentRepository repository = new JdbcPunishmentRepository(dataSource, IMMEDIATE);
            UUID target = UUID.randomUUID();
            Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
            Punishment punishment = Punishment.issue(PunishmentType.BAN, target, "Notch", null,
                    UUID.randomUUID(), "Admin", "Cheating", null);

            Punishment saved = repository.save(punishment).join();
            assertTrue(saved.id() > 0);

            Optional<Punishment> loaded = repository.findById(saved.id()).join();
            assertTrue(loaded.isPresent());
            assertEquals("Cheating", loaded.get().reason());
            assertTrue(loaded.get().active());

            // save() on an entity with a non-zero id updates the existing row.
            Punishment updated = repository.save(loaded.get().revoke(now, "Moderator")).join();
            assertFalse(updated.active());
            assertEquals(1, repository.findAll().join().size());
        } finally {
            ((AutoCloseable) dataSource).close();
        }
    }

    @Test
    void storesTheTemplateAndFindsOnlyTemplatePunishmentsOfTheTarget(@TempDir Path dir) throws Exception {
        DataSource dataSource = openDatabase(dir, "templates.db");
        try {
            JdbcPunishmentRepository repository = new JdbcPunishmentRepository(dataSource, IMMEDIATE);
            UUID target = UUID.randomUUID();
            repository.save(Punishment.issue(PunishmentType.WARN, target, "Steve", null, null, "Mod", "Spam", null, "spam")).join();
            repository.save(Punishment.issue(PunishmentType.WARN, target, "Steve", null, null, "Mod", "Be nice", null)).join();
            repository.save(Punishment.kick(target, "Steve", null, "Mod", "Spam", "spam")).join();
            repository.save(Punishment.issue(PunishmentType.WARN, UUID.randomUUID(), "Alex", null, null, "Mod", "Spam", null, "spam")).join();

            List<Punishment> found = repository.findWithTemplate(target).join();

            assertEquals(2, found.size());
            assertTrue(found.stream().allMatch(punishment -> "spam".equals(punishment.template())));
            Punishment warn = found.stream().filter(punishment -> punishment.type() == PunishmentType.WARN).findFirst().orElseThrow();
            Punishment revoked = repository.save(warn.revoke(Instant.now(), "Mod")).join();
            assertEquals("spam", repository.findById(revoked.id()).join().orElseThrow().template());
        } finally {
            ((AutoCloseable) dataSource).close();
        }
    }

    @Test
    void findsActiveBansOnlyWhileNotExpired(@TempDir Path dir) throws Exception {
        DataSource dataSource = openDatabase(dir, "active-ban.db");
        try {
            JdbcPunishmentRepository repository = new JdbcPunishmentRepository(dataSource, IMMEDIATE);
            UUID target = UUID.randomUUID();
            Instant now = Instant.now();

            repository.save(Punishment.issue(PunishmentType.TEMP_BAN, target, "Notch", null,
                    UUID.randomUUID(), "Admin", "Griefing", now.plusSeconds(3600))).join();

            assertTrue(repository.findActiveBan(target, now).join().isPresent());
            // Past the expiry instant, the same row is no longer "active" -
            // the active column itself is untouched (see class javadoc).
            assertTrue(repository.findActiveBan(target, now.plusSeconds(7200)).join().isEmpty());
        } finally {
            ((AutoCloseable) dataSource).close();
        }
    }

    @Test
    void findsActiveIpBans(@TempDir Path dir) throws Exception {
        DataSource dataSource = openDatabase(dir, "ip-ban.db");
        try {
            JdbcPunishmentRepository repository = new JdbcPunishmentRepository(dataSource, IMMEDIATE);
            UUID target = UUID.randomUUID();
            repository.save(Punishment.issue(PunishmentType.IP_BAN, target, "Notch", "203.0.113.5",
                    UUID.randomUUID(), "Admin", "Cheating", null)).join();

            assertTrue(repository.findActiveIpBan("203.0.113.5", Instant.now()).join().isPresent());
            assertTrue(repository.findActiveIpBan("203.0.113.6", Instant.now()).join().isEmpty());
        } finally {
            ((AutoCloseable) dataSource).close();
        }
    }

    @Test
    void findsActiveMutes(@TempDir Path dir) throws Exception {
        DataSource dataSource = openDatabase(dir, "mutes.db");
        try {
            JdbcPunishmentRepository repository = new JdbcPunishmentRepository(dataSource, IMMEDIATE);
            UUID target = UUID.randomUUID();
            repository.save(Punishment.issue(PunishmentType.MUTE, target, "Notch", null,
                    UUID.randomUUID(), "Admin", "Spam", null)).join();

            assertTrue(repository.findActiveMute(target, Instant.now()).join().isPresent());
        } finally {
            ((AutoCloseable) dataSource).close();
        }
    }

    @Test
    void findsActiveFreezes(@TempDir Path dir) throws Exception {
        DataSource dataSource = openDatabase(dir, "freezes.db");
        try {
            JdbcPunishmentRepository repository = new JdbcPunishmentRepository(dataSource, IMMEDIATE);
            UUID target = UUID.randomUUID();
            Punishment freeze = repository.save(Punishment.issue(PunishmentType.FREEZE, target, "Notch", null,
                    UUID.randomUUID(), "Admin", "Staff Freeze Tool", null)).join();

            assertTrue(repository.findActiveFreeze(target, Instant.now()).join().isPresent());

            repository.revokeById(freeze.id(), Instant.now(), "Moderator").join();
            assertTrue(repository.findActiveFreeze(target, Instant.now()).join().isEmpty());
        } finally {
            ((AutoCloseable) dataSource).close();
        }
    }

    @Test
    void findByQueryFiltersByTypeAndActiveState(@TempDir Path dir) throws Exception {
        DataSource dataSource = openDatabase(dir, "query.db");
        try {
            JdbcPunishmentRepository repository = new JdbcPunishmentRepository(dataSource, IMMEDIATE);
            UUID target = UUID.randomUUID();
            repository.save(Punishment.issue(PunishmentType.WARN, target, "Notch", null, UUID.randomUUID(), "Admin", "Rude", null)).join();
            Punishment ban = repository.save(Punishment.issue(PunishmentType.BAN, target, "Notch", null, UUID.randomUUID(), "Admin", "Cheating", null)).join();
            repository.revokeById(ban.id(), Instant.now(), "Moderator").join();

            List<Punishment> warnings = repository.findByQuery(new PunishmentQuery(target, Set.of(PunishmentType.WARN), null, 10)).join();
            assertEquals(1, warnings.size());

            List<Punishment> activeOnly = repository.findByQuery(new PunishmentQuery(target, null, true, 10)).join();
            assertEquals(1, activeOnly.size());
            assertEquals(PunishmentType.WARN, activeOnly.get(0).type());

            List<Punishment> all = repository.findByQuery(new PunishmentQuery(target, null, null, 10)).join();
            assertEquals(2, all.size());
        } finally {
            ((AutoCloseable) dataSource).close();
        }
    }

    @Test
    void expireOverdueFlipsOnlyPastExpiries(@TempDir Path dir) throws Exception {
        DataSource dataSource = openDatabase(dir, "expire.db");
        try {
            JdbcPunishmentRepository repository = new JdbcPunishmentRepository(dataSource, IMMEDIATE);
            UUID target = UUID.randomUUID();
            Instant now = Instant.now();
            Punishment overdue = repository.save(Punishment.issue(PunishmentType.TEMP_BAN, target, "Notch", null,
                    UUID.randomUUID(), "Admin", "Griefing", now.minusSeconds(10))).join();
            Punishment stillActive = repository.save(Punishment.issue(PunishmentType.TEMP_BAN, UUID.randomUUID(), "Steve", null,
                    UUID.randomUUID(), "Admin", "Griefing", now.plusSeconds(3600))).join();

            int affected = repository.expireOverdue(now).join();

            assertEquals(1, affected);
            assertFalse(repository.findById(overdue.id()).join().orElseThrow().active());
            assertTrue(repository.findById(stillActive.id()).join().orElseThrow().active());
        } finally {
            ((AutoCloseable) dataSource).close();
        }
    }

    @Test
    void revokeByIdIsConcurrencySafeOnlyOneOfTwoRacingRevokesSucceeds(@TempDir Path dir) throws Exception {
        DataSource dataSource = openDatabase(dir, "concurrent-revoke.db");
        try {
            JdbcPunishmentRepository repository = new JdbcPunishmentRepository(dataSource, IMMEDIATE);
            UUID target = UUID.randomUUID();
            Punishment warn = repository.save(Punishment.issue(PunishmentType.WARN, target, "Notch", null,
                    UUID.randomUUID(), "Admin", "Rude", null)).join();

            CountDownLatch start = new CountDownLatch(1);
            boolean[] firstSucceeded = new boolean[1];
            boolean[] secondSucceeded = new boolean[1];

            Thread first = new Thread(() -> {
                await(start);
                firstSucceeded[0] = repository.revokeById(warn.id(), Instant.now(), "ModeratorA").join().isPresent();
            });
            Thread second = new Thread(() -> {
                await(start);
                secondSucceeded[0] = repository.revokeById(warn.id(), Instant.now(), "ModeratorB").join().isPresent();
            });
            first.start();
            second.start();
            start.countDown();
            first.join();
            second.join();

            // WHERE id = ? AND active = TRUE guards the UPDATE - exactly one
            // of the two racing revokes can have actually flipped the row.
            assertTrue(firstSucceeded[0] ^ secondSucceeded[0], "exactly one of the two racing revokes should succeed");
            assertFalse(repository.findById(warn.id()).join().orElseThrow().active());
        } finally {
            ((AutoCloseable) dataSource).close();
        }
    }

    @Test
    void revokeActiveByTargetRevokesEveryMatchingRow(@TempDir Path dir) throws Exception {
        DataSource dataSource = openDatabase(dir, "revoke-target.db");
        try {
            JdbcPunishmentRepository repository = new JdbcPunishmentRepository(dataSource, IMMEDIATE);
            UUID target = UUID.randomUUID();
            repository.save(Punishment.issue(PunishmentType.BAN, target, "Notch", null, UUID.randomUUID(), "Admin", "Cheating", null)).join();
            repository.save(Punishment.issue(PunishmentType.IP_BAN, target, "Notch", "203.0.113.5", UUID.randomUUID(), "Admin", "Cheating", null)).join();
            repository.save(Punishment.issue(PunishmentType.WARN, target, "Notch", null, UUID.randomUUID(), "Admin", "Rude", null)).join();

            List<Punishment> revoked = repository.revokeActiveByTarget(
                    target, Set.of(PunishmentType.BAN, PunishmentType.TEMP_BAN, PunishmentType.IP_BAN), Instant.now(), "Moderator").join();

            assertEquals(2, revoked.size());
            assertTrue(repository.findActiveBan(target, Instant.now()).join().isEmpty());
            // The warning is untouched - only BAN/TEMP_BAN/IP_BAN types were revoked.
            List<Punishment> warnings = repository.findByQuery(new PunishmentQuery(target, Set.of(PunishmentType.WARN), true, 10)).join();
            assertEquals(1, warnings.size());
        } finally {
            ((AutoCloseable) dataSource).close();
        }
    }

    @Test
    void storesTheMuteKindAndListsEveryMuteInForce(@TempDir Path dir) throws Exception {
        DataSource dataSource = openDatabase(dir, "mute-kinds.db");
        try {
            JdbcPunishmentRepository repository = new JdbcPunishmentRepository(dataSource, IMMEDIATE);
            UUID target = UUID.randomUUID();
            Instant now = Instant.now();
            repository.save(Punishment.issue(PunishmentType.MUTE, target, "Notch", null, null, "Admin", "Shouting",
                    null, null, MuteKind.VOICE)).join();
            repository.save(Punishment.issue(PunishmentType.TEMP_MUTE, target, "Notch", null, null, "Admin", "Spam",
                    now.plusSeconds(600), null, MuteKind.CHAT)).join();
            repository.save(Punishment.issue(PunishmentType.TEMP_MUTE, target, "Notch", null, null, "Admin", "Old",
                    now.minusSeconds(60), null, MuteKind.FULL)).join();
            Punishment ban = repository.save(Punishment.issue(PunishmentType.BAN, target, "Notch", null, null, "Admin", "Grief",
                    null, null, MuteKind.VOICE)).join();

            List<Punishment> mutes = repository.findActiveMutes(target, now).join();

            assertEquals(Set.of(MuteKind.VOICE, MuteKind.CHAT), mutes.stream().map(Punishment::muteKind).collect(Collectors.toSet()));
            // Only mutes have a kind - the column stays empty for every other row.
            try (Connection connection = dataSource.getConnection();
                    Statement statement = connection.createStatement();
                    ResultSet row = statement.executeQuery("SELECT mute_kind FROM punishments WHERE id = " + ban.id())) {
                assertTrue(row.next());
                assertNull(row.getString(1));
            }
        } finally {
            ((AutoCloseable) dataSource).close();
        }
    }

    @Test
    void aMuteWithoutAKindIsAFullMute(@TempDir Path dir) throws Exception {
        DataSource dataSource = openDatabase(dir, "old-mutes.db");
        try {
            JdbcPunishmentRepository repository = new JdbcPunishmentRepository(dataSource, IMMEDIATE);
            UUID target = UUID.randomUUID();
            Punishment mute = repository.save(Punishment.issue(PunishmentType.MUTE, target, "Notch", null, null, "Admin", "Spam",
                    null, null, MuteKind.CHAT)).join();
            // What a row from before 1.5.0, or from an older server of a network, looks like.
            try (Connection connection = dataSource.getConnection();
                    Statement statement = connection.createStatement()) {
                statement.executeUpdate("UPDATE punishments SET mute_kind = NULL WHERE id = " + mute.id());
            }

            assertEquals(MuteKind.FULL, repository.findById(mute.id()).join().orElseThrow().muteKind());
        } finally {
            ((AutoCloseable) dataSource).close();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static DataSource openDatabase(Path dir, String fileName) {
        DataSource dataSource = DataSourceFactory.create(DatabaseConfig.sqlite(fileName), dir);
        MigrationRunner migrations = new MigrationRunner(dataSource, Logger.getLogger("test"));
        migrations.register(new ModerationPunishmentMigration());
        migrations.register(new ModerationPunishmentIndexMigration());
        migrations.register(new PunishmentTemplateMigration());
        migrations.register(new MuteKindMigration());
        migrations.runPending();
        return dataSource;
    }
}

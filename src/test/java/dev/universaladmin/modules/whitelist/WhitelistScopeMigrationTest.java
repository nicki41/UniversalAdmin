package dev.universaladmin.modules.whitelist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.universaladmin.modules.whitelist.jdbc.JdbcWhitelistEntryRepository;
import dev.universaladmin.modules.whitelist.jdbc.JdbcWhitelistStateRepository;
import dev.universaladmin.scheduler.TaskScheduler;
import dev.universaladmin.storage.DatabaseConfig;
import dev.universaladmin.storage.MigrationRunner;
import dev.universaladmin.storage.jdbc.DataSourceFactory;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.logging.Logger;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@link WhitelistScopeMigration} on a real SQLite database, read through the
 * scoped {@link JdbcWhitelistEntryRepository}: the old shared rows become
 * {@code local}, scopes stay apart, and a network server takes over only its
 * own players.
 */
class WhitelistScopeMigrationTest {

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

    private static final UUID STEVE = UUID.randomUUID();
    private static final UUID ALEX = UUID.randomUUID();

    @Test
    void copiesTheSharedRowsToLocalAndKeepsScopesApart(@TempDir Path dir) throws Exception {
        DataSource dataSource = DataSourceFactory.create(DatabaseConfig.sqlite("whitelist.db"), dir);
        try {
            MigrationRunner before = new MigrationRunner(dataSource, Logger.getLogger("test"));
            before.register(new WhitelistMigration());
            before.runPending();
            try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
                statement.executeUpdate("INSERT INTO whitelist_entries (player_id, player_name, source, added_by_name, added_at, "
                        + "reason, expires_at) VALUES ('" + STEVE + "', 'Steve', 'UNIVERSAL_ADMIN', 'Admin', 1, 'friend', 99)");
                statement.executeUpdate("INSERT INTO whitelist_entries (player_id, player_name, source, added_by_name, added_at) "
                        + "VALUES ('" + ALEX + "', 'Alex', 'UNIVERSAL_ADMIN', 'Admin', 2)");
            }
            MigrationRunner after = new MigrationRunner(dataSource, Logger.getLogger("test"));
            after.register(new WhitelistMigration());
            after.register(new WhitelistScopeMigration());
            after.runPending();

            JdbcWhitelistEntryRepository local = new JdbcWhitelistEntryRepository(dataSource, DIRECT, () -> "local");
            JdbcWhitelistEntryRepository lobby = new JdbcWhitelistEntryRepository(dataSource, DIRECT, () -> "server:lobby");
            JdbcWhitelistEntryRepository network = new JdbcWhitelistEntryRepository(dataSource, DIRECT, () -> "network");
            assertEquals(2, local.findAll().join().size());
            assertEquals("friend", local.findById(STEVE).join().orElseThrow().reason());
            assertEquals(Instant.ofEpochMilli(99), local.findById(STEVE).join().orElseThrow().expiresAt());
            assertTrue(lobby.findAll().join().isEmpty());

            // The lobby takes over Steve (on its native whitelist), not Alex - and only once.
            assertEquals(1, lobby.adopt("local", Set.of(STEVE)).join());
            assertEquals(0, lobby.adopt("local", Set.of(STEVE)).join());
            assertEquals(Optional.empty(), lobby.findById(ALEX).join());
            assertEquals("friend", lobby.findById(STEVE).join().orElseThrow().reason());

            // Removing on the lobby leaves the old copy alone, and the network list is separate.
            network.save(entry(ALEX, "Alex")).join();
            lobby.deleteById(STEVE).join();
            assertTrue(local.findById(STEVE).join().isPresent());
            assertEquals(1, network.findAll().join().size());
            assertTrue(lobby.findAll().join().isEmpty());
        } finally {
            ((AutoCloseable) dataSource).close();
        }
    }

    @Test
    void storesTheNetworkWhitelistSwitch(@TempDir Path dir) throws Exception {
        DataSource dataSource = DataSourceFactory.create(DatabaseConfig.sqlite("state.db"), dir);
        try {
            MigrationRunner runner = new MigrationRunner(dataSource, Logger.getLogger("test"));
            runner.register(new WhitelistMigration());
            runner.register(new WhitelistScopeMigration());
            runner.runPending();
            JdbcWhitelistStateRepository state = new JdbcWhitelistStateRepository(dataSource, DIRECT);

            assertEquals(Optional.empty(), state.load("network").join());
            state.save("network", true, "Admin").join();
            state.save("network", false, "Admin").join();
            assertEquals(Optional.of(false), state.load("network").join());
        } finally {
            ((AutoCloseable) dataSource).close();
        }
    }

    private static WhitelistEntry entry(UUID id, String name) {
        return new WhitelistEntry(id, name, WhitelistSource.UNIVERSAL_ADMIN, null, "Admin", Instant.now(), null, null, null);
    }
}

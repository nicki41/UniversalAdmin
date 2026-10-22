package dev.universaladmin.network.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.universaladmin.network.NetworkRepository.MessageRow;
import dev.universaladmin.network.NetworkRepository.PlayerRow;
import dev.universaladmin.network.NetworkRepository.ServerRow;
import dev.universaladmin.network.NetworkSchemaMigration;
import dev.universaladmin.network.NetworkVanishMigration;
import dev.universaladmin.scheduler.TaskScheduler;
import dev.universaladmin.storage.DatabaseConfig;
import dev.universaladmin.storage.MigrationRunner;
import dev.universaladmin.storage.jdbc.DataSourceFactory;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.logging.Logger;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The network tables against a real SQLite file - same portable SQL that runs against MySQL. */
class JdbcNetworkRepositoryTest {

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

    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    @TempDir
    Path dir;

    private DataSource dataSource;
    private JdbcNetworkRepository repository;

    @BeforeEach
    void setUp() {
        dataSource = DataSourceFactory.create(DatabaseConfig.sqlite("network.db"), dir);
        MigrationRunner migrations = new MigrationRunner(dataSource, Logger.getLogger("test"));
        migrations.register(new NetworkSchemaMigration());
        migrations.register(new NetworkVanishMigration());
        migrations.runPending();
        repository = new JdbcNetworkRepository(dataSource, IMMEDIATE);
    }

    @AfterEach
    void closeDatabase() throws Exception {
        // Windows cannot delete the temp directory while the file is open.
        if (dataSource instanceof AutoCloseable closeable) {
            closeable.close();
        }
    }

    private static ServerRow server(String name, int players) {
        return new ServerRow(name, players, 100, 19.9, "1.1.0", NOW, NOW);
    }

    @Test
    void heartbeatInsertsThenUpdatesTheServerAndReplacesItsPlayers() {
        UUID steve = UUID.randomUUID();
        UUID alex = UUID.randomUUID();
        repository.heartbeat(server("lobby", 2), List.of(
                new PlayerRow(steve, "Steve", "lobby", NOW), new PlayerRow(alex, "Alex", "lobby", NOW))).join();
        repository.heartbeat(server("lobby", 1), List.of(new PlayerRow(steve, "Steve", "lobby", NOW))).join();

        List<ServerRow> servers = repository.servers().join();
        assertEquals(1, servers.size());
        assertEquals(1, servers.get(0).onlinePlayers());
        assertEquals(List.of("Steve"), repository.players().join().stream().map(PlayerRow::name).toList());
    }

    @Test
    void storesWhetherAPlayerIsVanished() {
        UUID steve = UUID.randomUUID();
        UUID alex = UUID.randomUUID();
        repository.heartbeat(server("lobby", 1), List.of(
                new PlayerRow(steve, "Steve", "lobby", NOW, true), new PlayerRow(alex, "Alex", "lobby", NOW))).join();

        List<PlayerRow> players = repository.players().join();
        assertEquals(List.of(false, true), players.stream().map(PlayerRow::vanished).toList()); // ordered by name
    }

    @Test
    void aPlayerIsOnOneServerAtATimeAndAStaleQuitDoesNotRemoveTheNewEntry() {
        UUID steve = UUID.randomUUID();
        repository.playerJoined(new PlayerRow(steve, "Steve", "lobby", NOW)).join();
        // switching servers: the new server registers before the old one sees the quit
        repository.playerJoined(new PlayerRow(steve, "Steve", "survival", NOW)).join();
        repository.playerLeft(steve, "lobby").join();

        List<PlayerRow> players = repository.players().join();
        assertEquals(1, players.size());
        assertEquals("survival", players.get(0).server());

        repository.playerLeft(steve, "survival").join();
        assertTrue(repository.players().join().isEmpty());
    }

    @Test
    void removeServerDropsItAndItsPlayers() {
        repository.heartbeat(server("lobby", 1), List.of(new PlayerRow(UUID.randomUUID(), "Steve", "lobby", NOW))).join();
        repository.heartbeat(server("survival", 0), List.of()).join();

        repository.removeServer("lobby").join();

        assertEquals(List.of("survival"), repository.servers().join().stream().map(ServerRow::name).toList());
        assertTrue(repository.players().join().isEmpty());
    }

    @Test
    void messagesReachEveryOtherServerOrJustTheAddressedOne() {
        assertEquals(0L, repository.latestMessageId().join());
        repository.publish("lobby", null, "chat.staff", "{\"m\":\"hi\"}", NOW).join();
        repository.publish("lobby", "survival", "core.arrival", "{}", NOW).join();
        repository.publish("survival", null, "chat.staff", "{\"m\":\"hey\"}", NOW).join();

        List<MessageRow> forSurvival = repository.messagesSince(0, "survival", 100).join();
        assertEquals(List.of("chat.staff", "core.arrival"), forSurvival.stream().map(MessageRow::topic).toList());

        List<MessageRow> forCreative = repository.messagesSince(0, "creative", 100).join();
        assertEquals(2, forCreative.size());

        long latest = repository.latestMessageId().join();
        assertTrue(repository.messagesSince(latest, "creative", 100).join().isEmpty());
    }

    @Test
    void pruneDeletesOldMessagesOnly() {
        repository.publish("lobby", null, "old", "{}", NOW.minusSeconds(600)).join();
        repository.publish("lobby", null, "new", "{}", NOW).join();

        assertEquals(1, repository.pruneMessages(NOW.minusSeconds(300)).join());
        assertEquals(List.of("new"), repository.messagesSince(0, "survival", 100).join().stream().map(MessageRow::topic).toList());
    }
}

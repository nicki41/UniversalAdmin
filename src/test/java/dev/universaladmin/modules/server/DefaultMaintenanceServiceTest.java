package dev.universaladmin.modules.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.universaladmin.action.Actor;
import dev.universaladmin.network.NetworkMessage;
import dev.universaladmin.network.NetworkService;
import dev.universaladmin.scheduler.TaskScheduler;
import dev.universaladmin.settings.CoreSettings;
import dev.universaladmin.settings.SettingKey;
import dev.universaladmin.settings.SettingValue;
import dev.universaladmin.settings.SettingsService;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.logging.Logger;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

/**
 * Exercises {@link DefaultMaintenanceService} against an in-memory {@link
 * MaintenanceStateRepository} fake - same pattern as {@code PlayerServiceTest}.
 * Removing refused players reaches into live Bukkit state
 * ({@code Bukkit.getOnlinePlayers()}), which needs a running or mocked server
 * this project's unit tests deliberately don't stand up (same carve-out
 * {@code PlayerServiceTest} takes for {@code PlayerService#snapshot}) - the
 * tests only check that it is scheduled on the main thread.
 */
class DefaultMaintenanceServiceTest {

    private final Actor actor = Actor.system("test");
    private final RecordingScheduler scheduler = new RecordingScheduler();

    @Test
    void seedsFromCoreSettingsWhileTheRepositoryLoadIsPending() {
        FakeRepository repository = new FakeRepository();
        CompletableFuture<Map<String, MaintenanceState>> pendingLoad = new CompletableFuture<>();
        repository.pendingLoad = pendingLoad;
        DefaultMaintenanceService service = service(repository, standalone(), true, "<red>Maintenance!");

        assertTrue(service.current().enabled());
        assertEquals("<red>Maintenance!", service.kickMessage(MaintenanceScope.SERVER));

        pendingLoad.complete(Map.of(DefaultMaintenanceService.LOCAL_SCOPE, MaintenanceState.disabled()));
        assertFalse(service.current().enabled());
    }

    @Test
    void enablePersistsAndUpdatesCachedState() {
        FakeRepository repository = new FakeRepository();
        DefaultMaintenanceService service = service(repository, standalone());

        MaintenanceState result = service.enable(MaintenanceScope.SERVER, "scheduled downtime", null, false, actor).join();

        assertTrue(result.enabled());
        assertEquals("scheduled downtime", result.reason());
        assertEquals(result, service.current());
        assertEquals(result, repository.rows.get(DefaultMaintenanceService.LOCAL_SCOPE));
        assertTrue(service.active());
    }

    @Test
    void disablePreservesReasonAndMessageButClearsEnabled() {
        DefaultMaintenanceService service = service(new FakeRepository(), standalone());
        service.enable(MaintenanceScope.SERVER, "reason", "custom message", false, actor).join();

        MaintenanceState result = service.disable(MaintenanceScope.SERVER, actor).join();

        assertFalse(result.enabled());
        assertEquals("reason", result.reason());
        assertEquals("custom message", result.message());
    }

    @Test
    void setAllowedPlayersNormalizesNamesToLowercase() {
        DefaultMaintenanceService service = service(new FakeRepository(), standalone());

        MaintenanceState result = service.setAllowedPlayers(MaintenanceScope.SERVER, Set.of("Notch", "JEB_"), actor).join();

        assertEquals(Set.of("notch", "jeb_"), result.allowedPlayers());
    }

    @Test
    void isAllowedIsAlwaysTrueWhenMaintenanceIsDisabled() {
        DefaultMaintenanceService service = service(new FakeRepository(), standalone());

        assertTrue(service.isAllowed(player("Steve", false)));
        assertFalse(service.active());
    }

    @Test
    void isAllowedChecksBypassPermissionAndAllowList() {
        DefaultMaintenanceService service = service(new FakeRepository(), standalone());
        service.enable(MaintenanceScope.SERVER, "reason", null, false, actor).join();
        service.setAllowedPlayers(MaintenanceScope.SERVER, Set.of("Alice"), actor).join();

        assertTrue(service.isAllowed(player("Steve", true)));
        assertTrue(service.isAllowed(player("alice", false)));
        assertEquals(Optional.of(MaintenanceScope.SERVER), service.blockedBy(player("Bob", false)));
    }

    @Test
    void networkScopeNeedsNetworkMode() {
        FakeRepository repository = new FakeRepository();
        DefaultMaintenanceService service = service(repository, standalone());

        CompletionException error = assertThrows(CompletionException.class,
                () -> service.enable(MaintenanceScope.NETWORK, "reason", null, false, actor).join());

        assertTrue(error.getCause() instanceof IllegalStateException);
        assertFalse(service.networkAvailable());
        assertFalse(service.current(MaintenanceScope.NETWORK).enabled());
        assertNull(repository.rows.get(DefaultMaintenanceService.NETWORK_SCOPE));
    }

    @Test
    void eachNetworkServerKeepsItsOwnMaintenance() {
        FakeRepository repository = new FakeRepository();
        repository.rows.put("server:lobby", enabledState(Set.of()));
        NetworkService network = network("survival");
        DefaultMaintenanceService service = service(repository, network);

        assertFalse(service.current().enabled());
        service.enable(MaintenanceScope.SERVER, "new world", null, false, actor).join();

        assertTrue(repository.rows.get("server:survival").enabled());
        assertNull(repository.rows.get(DefaultMaintenanceService.NETWORK_SCOPE));
        verify(network, never()).publish(any(), anyMap());
    }

    @Test
    void networkMaintenanceIsCheckedFirstWithItsOwnAllowList() {
        NetworkService network = network("survival");
        DefaultMaintenanceService service = service(new FakeRepository(), network);
        service.enable(MaintenanceScope.SERVER, "server", "<red>Survival is closed", false, actor).join();
        service.setAllowedPlayers(MaintenanceScope.SERVER, Set.of("Alice"), actor).join();
        service.enable(MaintenanceScope.NETWORK, "network", "<red>Network is closed", false, actor).join();
        service.setAllowedPlayers(MaintenanceScope.NETWORK, Set.of("Bob", "Alice"), actor).join();

        assertEquals(Optional.empty(), service.blockedBy(player("Alice", false)));
        assertEquals(Optional.of(MaintenanceScope.SERVER), service.blockedBy(player("Bob", false)));
        assertEquals(Optional.of(MaintenanceScope.NETWORK), service.blockedBy(player("Carol", false)));
        assertEquals(Optional.empty(), service.blockedBy(player("Dave", true)));
        assertEquals("<red>Network is closed", service.kickMessage(MaintenanceScope.NETWORK));
        // Once for switching it on, once for the allow-list.
        verify(network, times(2)).publish(DefaultMaintenanceService.NETWORK_TOPIC, Map.of("kick", "false"));
    }

    @Test
    void aNetworkServerWithoutItsOwnRowReadsTheRowFromBeforeTheUpgrade() {
        FakeRepository repository = new FakeRepository();
        repository.rows.put(DefaultMaintenanceService.LOCAL_SCOPE, enabledState(Set.of("alice")));
        DefaultMaintenanceService service = service(repository, network("survival"));

        assertTrue(service.current().enabled());

        service.disable(MaintenanceScope.SERVER, actor).join();
        assertFalse(repository.rows.get("server:survival").enabled());
        assertTrue(repository.rows.get(DefaultMaintenanceService.LOCAL_SCOPE).enabled());
    }

    @Test
    void aChangeMessageFromAnotherServerReloadsTheNetworkState() {
        FakeRepository repository = new FakeRepository();
        DefaultMaintenanceService service = service(repository, network("survival"));
        assertFalse(service.current(MaintenanceScope.NETWORK).enabled());

        repository.rows.put(DefaultMaintenanceService.NETWORK_SCOPE, enabledState(Set.of()));
        service.onMessage(new NetworkMessage(DefaultMaintenanceService.NETWORK_TOPIC, "lobby", Map.of("kick", "false"), Instant.now()));

        assertTrue(service.current(MaintenanceScope.NETWORK).enabled());
        assertTrue(service.active());
        assertEquals(0, scheduler.mainThreadTasks.get());

        service.onMessage(new NetworkMessage(DefaultMaintenanceService.NETWORK_TOPIC, "lobby", Map.of("kick", "true"), Instant.now()));
        assertEquals(1, scheduler.mainThreadTasks.get());
    }

    @Test
    void learningTheProxyNameLoadsThatServersStateAndRemovesRefusedPlayers() {
        FakeRepository repository = new FakeRepository();
        repository.rows.put("server:survival", enabledState(Set.of()));
        NetworkService network = network("server-25602");
        DefaultMaintenanceService service = service(repository, network);
        assertFalse(service.current().enabled());

        when(network.serverName()).thenReturn("survival");
        service.refresh().join();

        assertTrue(service.current().enabled());
        assertEquals(1, scheduler.mainThreadTasks.get());
    }

    @Test
    void aRefreshThatOverlapsASaveKeepsTheSavedState() {
        FakeRepository repository = new FakeRepository();
        DefaultMaintenanceService service = service(repository, standalone());
        CompletableFuture<Map<String, MaintenanceState>> pendingLoad = new CompletableFuture<>();
        repository.pendingLoad = pendingLoad;
        CompletableFuture<Void> refresh = service.refresh();

        service.enable(MaintenanceScope.SERVER, "reason", null, false, actor).join();
        pendingLoad.complete(Map.of(DefaultMaintenanceService.LOCAL_SCOPE, MaintenanceState.disabled()));
        refresh.join();

        assertTrue(service.current().enabled());
    }

    private DefaultMaintenanceService service(FakeRepository repository, NetworkService network) {
        return service(repository, network, false, "default");
    }

    private DefaultMaintenanceService service(
            FakeRepository repository, NetworkService network, boolean maintenanceEnabled, String kickMessage) {
        Map<SettingKey<?>, Object> values = new HashMap<>();
        values.put(CoreSettings.MAINTENANCE_ENABLED, maintenanceEnabled);
        values.put(CoreSettings.MAINTENANCE_KICK_MESSAGE, kickMessage);
        return new DefaultMaintenanceService(repository, new FakeSettingsService(values), scheduler, Logger.getAnonymousLogger(), network);
    }

    private static NetworkService standalone() {
        NetworkService network = mock(NetworkService.class);
        when(network.enabled()).thenReturn(false);
        when(network.serverName()).thenReturn("server-25565");
        return network;
    }

    private static NetworkService network(String serverName) {
        NetworkService network = mock(NetworkService.class);
        when(network.enabled()).thenReturn(true);
        when(network.serverName()).thenReturn(serverName);
        return network;
    }

    private static MaintenanceState enabledState(Set<String> allowed) {
        return new MaintenanceState(true, "reason", null, allowed, Instant.EPOCH, "tester");
    }

    private static Player player(String name, boolean bypass) {
        Player player = mock(Player.class);
        when(player.hasPermission(ServerPermissions.BYPASS_MAINTENANCE.value())).thenReturn(bypass);
        when(player.getName()).thenReturn(name);
        return player;
    }

    /** Runs nothing on a "main thread" - only counts what would have run there. */
    private static final class RecordingScheduler implements TaskScheduler {
        private final AtomicInteger mainThreadTasks = new AtomicInteger();

        @Override
        public <T> CompletableFuture<T> supplyAsync(Supplier<T> task) {
            throw new UnsupportedOperationException("not expected in this test");
        }

        @Override
        public CompletableFuture<Void> runAsync(Runnable task) {
            throw new UnsupportedOperationException("not expected in this test");
        }

        @Override
        public void runOnMainThread(Runnable task) {
            mainThreadTasks.incrementAndGet();
        }

        @Override
        public void close() {
        }
    }

    private record FakeSettingsService(Map<SettingKey<?>, Object> values) implements SettingsService {
        @Override
        @SuppressWarnings("unchecked")
        public <T> T get(SettingKey<T> key) {
            return (T) values.get(key);
        }

        @Override
        public <T> SettingValue<T> getValue(SettingKey<T> key) {
            throw new UnsupportedOperationException("not needed for this test");
        }

        @Override
        public dev.universaladmin.settings.ConfigReloadResult reload() {
            throw new UnsupportedOperationException("not needed for this test");
        }
    }

    /** Rows by scope key; {@link #pendingLoad}, when set, is what the next load returns instead of the rows. */
    private static final class FakeRepository implements MaintenanceStateRepository {
        private final Map<String, MaintenanceState> rows = new ConcurrentHashMap<>();
        private volatile CompletableFuture<Map<String, MaintenanceState>> pendingLoad;

        @Override
        public CompletableFuture<Map<String, MaintenanceState>> load(Set<String> scopes) {
            CompletableFuture<Map<String, MaintenanceState>> pending = pendingLoad;
            if (pending != null) {
                pendingLoad = null;
                return pending;
            }
            Map<String, MaintenanceState> found = new HashMap<>();
            scopes.forEach(scope -> {
                if (rows.containsKey(scope)) {
                    found.put(scope, rows.get(scope));
                }
            });
            return CompletableFuture.completedFuture(found);
        }

        @Override
        public CompletableFuture<Void> save(String scope, MaintenanceState state) {
            rows.put(scope, state);
            return CompletableFuture.completedFuture(null);
        }
    }
}

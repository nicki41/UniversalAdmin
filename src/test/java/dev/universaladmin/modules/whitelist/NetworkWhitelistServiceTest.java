package dev.universaladmin.modules.whitelist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.universaladmin.action.Actor;
import dev.universaladmin.network.NetworkMessage;
import dev.universaladmin.network.NetworkService;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

/** The network whitelist against in-memory repositories: who it lets in, and that changes reach the other servers. */
class NetworkWhitelistServiceTest {

    private static final UUID STEVE = UUID.randomUUID();
    private static final UUID ALEX = UUID.randomUUID();

    private final Actor admin = Actor.system("test");
    private final MemoryEntries entries = new MemoryEntries();
    private final MemoryState state = new MemoryState();

    @Test
    void whileOffEveryoneGetsIn() {
        NetworkWhitelistService service = service(network(true));
        service.refresh().join();

        assertFalse(service.enabled());
        assertTrue(service.allows(STEVE, Instant.now()));
    }

    @Test
    void whileOnOnlyPlayersWithAValidEntryGetIn() {
        NetworkWhitelistService service = service(network(true));
        service.add(STEVE, "Steve", admin, null, null, null).join();
        service.add(ALEX, "Alex", admin, "trial", null, Instant.now().plus(Duration.ofHours(1))).join();
        service.setEnabled(true, admin).join();

        Instant now = Instant.now();
        assertTrue(service.allows(STEVE, now));
        assertTrue(service.allows(ALEX, now));
        assertFalse(service.allows(ALEX, now.plus(Duration.ofHours(2))));
        assertFalse(service.allows(UUID.randomUUID(), now));
        assertEquals(List.of("Alex", "Steve"), service.names());
    }

    @Test
    void everyChangeIsAnnouncedAndOtherServersReadItAgain() {
        NetworkService network = network(true);
        NetworkWhitelistService here = service(network);
        here.add(STEVE, "Steve", admin, null, null, null).join();
        here.setEnabled(true, admin).join();
        here.remove(STEVE).join();
        verify(network, times(3)).publish(eq(NetworkWhitelistService.TOPIC), anyMap());

        NetworkWhitelistService elsewhere = service(network(true));
        entries.save(new WhitelistEntry(ALEX, "Alex", WhitelistSource.UNIVERSAL_ADMIN, null, "Admin", Instant.now(), null, null, null))
                .join();
        elsewhere.onMessage(new NetworkMessage(NetworkWhitelistService.TOPIC, "lobby", Map.of(), Instant.now()));

        assertTrue(elsewhere.enabled());
        assertTrue(elsewhere.allows(ALEX, Instant.now()));
        assertFalse(elsewhere.allows(STEVE, Instant.now()));
    }

    @Test
    void withoutNetworkModeThereIsNoNetworkWhitelist() {
        NetworkWhitelistService service = service(network(false));

        assertFalse(service.available());
        assertTrue(service.allows(STEVE, Instant.now()));
        assertThrows(CompletionException.class, () -> service.setEnabled(true, admin).join());
        assertThrows(CompletionException.class, () -> service.add(STEVE, "Steve", admin, null, null, null).join());
    }

    @Test
    void expiredEntriesAreRemovedQuietly() {
        NetworkWhitelistService service = service(network(true));
        service.add(STEVE, "Steve", admin, null, null, Instant.now().minusSeconds(5)).join();
        service.add(ALEX, "Alex", admin, null, null, null).join();

        assertEquals(1, service.removeExpired().join());
        assertEquals(Set.of(ALEX), entries.store.keySet());
    }

    private NetworkWhitelistService service(NetworkService network) {
        return new NetworkWhitelistService(entries, state, network, Logger.getAnonymousLogger());
    }

    private static NetworkService network(boolean enabled) {
        NetworkService network = mock(NetworkService.class);
        when(network.enabled()).thenReturn(enabled);
        when(network.player(org.mockito.ArgumentMatchers.any(UUID.class))).thenReturn(Optional.empty());
        return network;
    }

    private static final class MemoryEntries implements WhitelistEntryRepository {
        private final Map<UUID, WhitelistEntry> store = new ConcurrentHashMap<>();

        @Override
        public CompletableFuture<Optional<WhitelistEntry>> findById(UUID id) {
            return CompletableFuture.completedFuture(Optional.ofNullable(store.get(id)));
        }

        @Override
        public CompletableFuture<List<WhitelistEntry>> findAll() {
            return CompletableFuture.completedFuture(new ArrayList<>(store.values()));
        }

        @Override
        public CompletableFuture<WhitelistEntry> save(WhitelistEntry entity) {
            store.put(entity.playerId(), entity);
            return CompletableFuture.completedFuture(entity);
        }

        @Override
        public CompletableFuture<Void> deleteById(UUID id) {
            store.remove(id);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Integer> adopt(String fromScope, Set<UUID> playerIds) {
            return CompletableFuture.completedFuture(0);
        }
    }

    private static final class MemoryState implements WhitelistStateRepository {
        private final Map<String, Boolean> store = new ConcurrentHashMap<>();

        @Override
        public CompletableFuture<Optional<Boolean>> load(String scope) {
            return CompletableFuture.completedFuture(Optional.ofNullable(store.get(scope)));
        }

        @Override
        public CompletableFuture<Void> save(String scope, boolean enabled, String updatedBy) {
            store.put(scope, enabled);
            return CompletableFuture.completedFuture(null);
        }
    }
}

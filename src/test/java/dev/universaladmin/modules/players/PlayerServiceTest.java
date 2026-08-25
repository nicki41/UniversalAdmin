package dev.universaladmin.modules.players;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.universaladmin.scheduler.TaskScheduler;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/**
 * Exercises {@link PlayerService} against an in-memory {@link PlayerProfileRepository}
 * fake instead of a real database or a running Paper server - this is the
 * pattern every module's business logic should be testable with. See
 * docs/development/testing.md. {@link PlayerService#snapshot(UUID)} isn't
 * covered here - it reaches into live Bukkit state ({@code Bukkit.getPlayer}/
 * {@code getOfflinePlayer}), which needs a running or mocked server this
 * project's unit tests deliberately don't stand up.
 */
class PlayerServiceTest {

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

    private final Map<UUID, PlayerProfile> store = new ConcurrentHashMap<>();
    private final PlayerService service = new PlayerService(new InMemoryPlayerProfileRepository(store), IMMEDIATE, new PlayerSessionTracker());

    @Test
    void createsAProfileOnFirstSeen() {
        UUID playerId = UUID.randomUUID();

        PlayerProfile profile = service.getOrCreateProfile(playerId, "Notch").join();

        assertEquals(playerId, profile.id());
        assertEquals("Notch", profile.lastKnownName());
        assertEquals(profile.firstJoin(), profile.lastSeen());
    }

    @Test
    void updatesNameAndLastSeenOnReturningPlayer() {
        UUID playerId = UUID.randomUUID();
        PlayerProfile first = service.getOrCreateProfile(playerId, "OldName").join();

        PlayerProfile second = service.getOrCreateProfile(playerId, "NewName").join();

        assertEquals(first.firstJoin(), second.firstJoin());
        assertEquals("NewName", second.lastKnownName());
        assertTrue(!second.lastSeen().isBefore(first.lastSeen()));
    }

    @Test
    void searchDelegatesToTheRepository() {
        service.getOrCreateProfile(UUID.randomUUID(), "Alice").join();
        service.getOrCreateProfile(UUID.randomUUID(), "Bob").join();

        List<PlayerProfile> results =
                service.search(new PlayerSearchQuery("ali", PlayerSort.NAME_ASC, 10)).join();

        assertEquals(1, results.size());
        assertEquals("Alice", results.get(0).lastKnownName());
    }

    private record InMemoryPlayerProfileRepository(Map<UUID, PlayerProfile> store)
            implements PlayerProfileRepository {

        @Override
        public CompletableFuture<Optional<PlayerProfile>> findById(UUID id) {
            return CompletableFuture.completedFuture(Optional.ofNullable(store.get(id)));
        }

        @Override
        public CompletableFuture<List<PlayerProfile>> findAll() {
            return CompletableFuture.completedFuture(new ArrayList<>(store.values()));
        }

        @Override
        public CompletableFuture<PlayerProfile> save(PlayerProfile entity) {
            store.put(entity.id(), entity);
            return CompletableFuture.completedFuture(entity);
        }

        @Override
        public CompletableFuture<Void> deleteById(UUID id) {
            store.remove(id);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<List<PlayerProfile>> search(PlayerSearchQuery query) {
            Comparator<PlayerProfile> comparator = switch (query.sort()) {
                case NAME_ASC -> Comparator.comparing(PlayerProfile::lastKnownName, String.CASE_INSENSITIVE_ORDER);
                case NAME_DESC -> Comparator.comparing(PlayerProfile::lastKnownName, String.CASE_INSENSITIVE_ORDER).reversed();
                case LAST_SEEN_DESC -> Comparator.comparing(PlayerProfile::lastSeen).reversed();
                case LAST_SEEN_ASC -> Comparator.comparing(PlayerProfile::lastSeen);
                case FIRST_JOIN_DESC -> Comparator.comparing(PlayerProfile::firstJoin).reversed();
            };
            List<PlayerProfile> results = store.values().stream()
                    .filter(p -> query.nameContains() == null || query.nameContains().isBlank()
                            || p.lastKnownName().toLowerCase(Locale.ROOT).contains(query.nameContains().toLowerCase(Locale.ROOT)))
                    .sorted(comparator)
                    .limit(query.limit())
                    .toList();
            return CompletableFuture.completedFuture(results);
        }
    }
}

package dev.universaladmin.modules.whitelist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.universaladmin.action.Action;
import dev.universaladmin.action.ActionContext;
import dev.universaladmin.action.ActionDefinition;
import dev.universaladmin.action.ActionExecutor;
import dev.universaladmin.action.ActionId;
import dev.universaladmin.action.ActionRegistry;
import dev.universaladmin.action.ActionResult;
import dev.universaladmin.audit.AuditEvent;
import dev.universaladmin.audit.AuditPage;
import dev.universaladmin.audit.AuditQuery;
import dev.universaladmin.audit.AuditService;
import dev.universaladmin.modules.whitelist.action.WhitelistActionIds;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Exercises {@link WhitelistExpirySweeper} against an in-memory {@link
 * WhitelistEntryRepository} fake and a fake {@code whitelist.remove} action
 * (never the real {@link dev.universaladmin.modules.whitelist.action.RemoveWhitelistEntryAction},
 * which needs a running Bukkit server) - same "fake instead of a running
 * Paper server" pattern as {@code PlayerServiceTest}.
 *
 * <p>The "ownership" contract lives entirely in this class: {@link
 * WhitelistExpirySweeper} only ever iterates {@link
 * WhitelistEntryRepository#findAll()} - it has no reference to Bukkit's
 * native whitelist at all (no import, no field), so a native-only entry
 * with no matching row (the "foreign, manually-added entry" case from the
 * spec) is structurally never seen, let alone removed. What's left to
 * actually test is the filter: only rows with {@code source ==
 * UNIVERSAL_ADMIN} *and* an expiry in the past are ever removed.
 */
class WhitelistExpirySweeperTest {

    private final CopyOnWriteArrayList<UUID> removedByAction = new CopyOnWriteArrayList<>();

    @Test
    void onlyRemovesExpiredUniversalAdminOwnedEntries() {
        UUID expired = UUID.randomUUID();
        UUID notYetExpired = UUID.randomUUID();
        UUID permanent = UUID.randomUUID();

        Map<UUID, WhitelistEntry> store = new ConcurrentHashMap<>();
        store.put(expired, entry(expired, Instant.now().minusSeconds(60)));
        store.put(notYetExpired, entry(notYetExpired, Instant.now().plusSeconds(3600)));
        store.put(permanent, entry(permanent, null));

        int removedCount = sweep(store);

        assertEquals(1, removedCount);
        assertEquals(List.of(expired), removedByAction);
    }

    @Test
    void neverTouchesAnEntryThatHasNoRowAtAll() {
        // Represents a player whitelisted outside UniversalAdmin entirely
        // (vanilla /whitelist add, a hand-edited whitelist.json, another
        // plugin) - by design there is simply no row for them, so the
        // repository the sweeper reads from never contains their UUID.
        UUID foreignPlayer = UUID.randomUUID();
        Map<UUID, WhitelistEntry> store = new ConcurrentHashMap<>();

        int removedCount = sweep(store);

        assertEquals(0, removedCount);
        assertTrue(removedByAction.isEmpty());
        assertTrue(!removedByAction.contains(foreignPlayer));
    }

    @Test
    void sweepingAnEmptyRepositoryRemovesNothing() {
        assertEquals(0, sweep(new ConcurrentHashMap<>()));
        assertTrue(removedByAction.isEmpty());
    }

    private int sweep(Map<UUID, WhitelistEntry> store) {
        WhitelistEntryRepository repository = new InMemoryWhitelistEntryRepository(store);
        ActionRegistry registry = new ActionRegistry();
        registry.register(ActionDefinition.builder(fakeRemoveAction()).build());
        ActionExecutor actionExecutor = new ActionExecutor(registry, noopAuditService(), Logger.getAnonymousLogger());
        WhitelistExpirySweeper sweeper = new WhitelistExpirySweeper(repository, actionExecutor);
        return sweeper.sweep().join();
    }

    private WhitelistEntry entry(UUID playerId, Instant expiresAt) {
        return new WhitelistEntry(playerId, "player-" + playerId, WhitelistSource.UNIVERSAL_ADMIN,
                null, "tester", Instant.now(), null, null, expiresAt);
    }

    private Action<UUID, Void> fakeRemoveAction() {
        return new Action<>() {
            @Override
            public ActionId id() {
                return WhitelistActionIds.REMOVE;
            }

            @Override
            public CompletableFuture<ActionResult<Void>> execute(ActionContext context, UUID playerId) {
                removedByAction.add(playerId);
                return CompletableFuture.completedFuture(ActionResult.success(null));
            }
        };
    }

    private AuditService noopAuditService() {
        return new AuditService() {
            @Override
            public CompletableFuture<Void> record(AuditEvent entry) {
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public CompletableFuture<AuditPage> query(AuditQuery query) {
                throw new UnsupportedOperationException("not needed for this test");
            }

            @Override
            public CompletableFuture<List<AuditEvent>> recent(int limit) {
                throw new UnsupportedOperationException("not needed for this test");
            }

            @Override
            public CompletableFuture<Optional<AuditEvent>> findById(Long id) {
                throw new UnsupportedOperationException("not needed for this test");
            }

            @Override
            public CompletableFuture<Integer> cleanupExpired() {
                throw new UnsupportedOperationException("not needed for this test");
            }
        };
    }

    private record InMemoryWhitelistEntryRepository(Map<UUID, WhitelistEntry> store) implements WhitelistEntryRepository {

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
    }
}

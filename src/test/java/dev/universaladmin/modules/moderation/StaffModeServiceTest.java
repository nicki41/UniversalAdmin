package dev.universaladmin.modules.moderation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.universaladmin.scheduler.TaskScheduler;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

/**
 * Exercises {@link StaffModeService}'s <b>decision</b> logic only - the
 * "snapshot already exists" / "no snapshot to restore" early-return
 * branches, which return before ever touching the live {@code Player}
 * inventory or hopping through {@link TaskScheduler#runOnMainThread}. The
 * actual enter/exit inventory mutation needs a running Paper server (real
 * {@code ItemStack} contents, {@code Bukkit.getServer()}), same documented
 * exclusion {@code PlayerServiceTest} uses for {@code snapshot()} - not
 * unit-testable here. {@link Player#getUniqueId()} is mocked (an
 * already-established pattern in this codebase, see {@code
 * GuiClickContextTest}/{@code GuiListenerTest}), never a live entity.
 */
class StaffModeServiceTest {

    private static final TaskScheduler NEVER_INVOKED = new TaskScheduler() {
        @Override
        public <T> CompletableFuture<T> supplyAsync(Supplier<T> task) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<Void> runAsync(Runnable task) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void runOnMainThread(Runnable task) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void close() {
        }
    };

    @Test
    void enterFailsWithoutTouchingTheInventoryWhenASnapshotAlreadyExists() {
        UUID playerId = UUID.randomUUID();
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerId);

        Map<UUID, StaffModeSnapshot> store = new HashMap<>();
        store.put(playerId, new StaffModeSnapshot(playerId, new byte[0], GameMode.SURVIVAL, 0f, 0, false, false, Instant.now()));
        StaffModeService service = new StaffModeService(new InMemoryStaffModeSnapshotRepository(store),
                new StaffModeState(), new GodmodeState(), new CollisionState(), null, null, null, NEVER_INVOKED);

        // NEVER_INVOKED would throw if this reached scheduler.runOnMainThread -
        // it doesn't, because the "already exists" check short-circuits first.
        assertFalse(service.enter(player).join());
    }

    @Test
    void exitReturnsFalseWithoutTouchingTheInventoryWhenNoSnapshotExists() {
        UUID playerId = UUID.randomUUID();
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerId);

        StaffModeService service = new StaffModeService(new InMemoryStaffModeSnapshotRepository(new HashMap<>()),
                new StaffModeState(), new GodmodeState(), new CollisionState(), null, null, null, NEVER_INVOKED);

        assertFalse(service.exit(player).join());
    }

    private record InMemoryStaffModeSnapshotRepository(Map<UUID, StaffModeSnapshot> store) implements StaffModeSnapshotRepository {

        @Override
        public CompletableFuture<Optional<StaffModeSnapshot>> findById(UUID id) {
            return CompletableFuture.completedFuture(Optional.ofNullable(store.get(id)));
        }

        @Override
        public CompletableFuture<List<StaffModeSnapshot>> findAll() {
            return CompletableFuture.completedFuture(new ArrayList<>(store.values()));
        }

        @Override
        public CompletableFuture<StaffModeSnapshot> save(StaffModeSnapshot entity) {
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

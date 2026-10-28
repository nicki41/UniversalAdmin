package dev.universaladmin.modules.moderation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.universaladmin.action.ActionContext;
import dev.universaladmin.action.ActionResult;
import dev.universaladmin.action.Actor;
import dev.universaladmin.action.Source;
import dev.universaladmin.modules.moderation.action.GodmodeAction;
import dev.universaladmin.permission.PermissionEvaluator;
import dev.universaladmin.scheduler.TaskScheduler;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

/**
 * Godmode is the mark in the player's data, which Minecraft keeps across
 * restarts - not {@code isInvulnerable()}, which on Paper 26 is also true
 * for the protection right after joining.
 */
class GodmodeSyncTest {

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

    private final UUID id = UUID.randomUUID();
    private final Player player = mock(Player.class);
    private final PersistentDataContainer data = mock(PersistentDataContainer.class);
    private final Deque<Runnable> laterTasks = new ArrayDeque<>();
    private final Plugin plugin = mock(Plugin.class);

    GodmodeSyncTest() {
        when(player.getUniqueId()).thenReturn(id);
        when(player.getPersistentDataContainer()).thenReturn(data);
        Server server = mock(Server.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getScheduler()).thenReturn(scheduler);
        when(server.getPlayer(id)).thenReturn(player);
        when(player.hasPermission(ModerationPermissions.GODMODE.value())).thenReturn(true);
        when(scheduler.runTaskLater(eq(plugin), any(Runnable.class), anyLong())).thenAnswer(invocation -> {
            laterTasks.add(invocation.getArgument(1));
            return null;
        });
    }

    private void runLaterTasks() {
        while (!laterTasks.isEmpty()) {
            laterTasks.poll().run();
        }
    }

    @Test
    void aMarkedPlayerHasGodmodeOnAfterJoining() {
        GodmodeState state = new GodmodeState();
        GodmodeSyncListener listener = new GodmodeSyncListener(state, plugin);
        when(data.has(GodmodeMarker.KEY, PersistentDataType.BYTE)).thenReturn(true);

        listener.onJoin(new PlayerJoinEvent(player, (Component) null));
        assertTrue(state.isEnabled(id));
        verify(player).setInvulnerable(true);

        listener.onQuit(new PlayerQuitEvent(player, (Component) null, PlayerQuitEvent.QuitReason.DISCONNECTED));
        assertFalse(state.isEnabled(id));
    }

    @Test
    void theProtectionAfterJoiningIsNotGodmode() {
        GodmodeState state = new GodmodeState();
        GodmodeSyncListener listener = new GodmodeSyncListener(state, plugin);
        // Invulnerable while joining, gone by the time of the check.
        when(player.isInvulnerable()).thenReturn(true, false);

        listener.onJoin(new PlayerJoinEvent(player, (Component) null));
        assertFalse(state.isEnabled(id));
        runLaterTasks();

        assertFalse(state.isEnabled(id));
        verify(data, never()).set(any(), any(), any());
    }

    @Test
    void godmodeFromBeforeTheMarkIsRecognisedOnceTheProtectionIsOver() {
        GodmodeState state = new GodmodeState();
        GodmodeSyncListener listener = new GodmodeSyncListener(state, plugin);
        when(player.isInvulnerable()).thenReturn(true);

        listener.onJoin(new PlayerJoinEvent(player, (Component) null));
        assertFalse(state.isEnabled(id));
        runLaterTasks();

        assertTrue(state.isEnabled(id));
        verify(data).set(GodmodeMarker.KEY, PersistentDataType.BYTE, (byte) 1);
    }

    @Test
    void hitProtectionAtBothChecksIsNotGodmode() {
        GodmodeState state = new GodmodeState();
        GodmodeSyncListener listener = new GodmodeSyncListener(state, plugin);
        // Hit just before each check: invulnerable, but only for the hit.
        when(player.isInvulnerable()).thenReturn(true);
        when(player.getNoDamageTicks()).thenReturn(15);

        listener.onJoin(new PlayerJoinEvent(player, (Component) null));
        runLaterTasks();

        assertFalse(state.isEnabled(id));
        verify(data, never()).set(any(), any(), any());
    }

    @Test
    void aPlayerWithoutTheGodmodePermissionIsNeverMarked() {
        GodmodeState state = new GodmodeState();
        GodmodeSyncListener listener = new GodmodeSyncListener(state, plugin);
        when(player.isInvulnerable()).thenReturn(true);
        when(player.hasPermission(ModerationPermissions.GODMODE.value())).thenReturn(false);

        listener.onJoin(new PlayerJoinEvent(player, (Component) null));
        runLaterTasks();

        assertFalse(state.isEnabled(id));
        verify(data, never()).set(any(), any(), any());
    }

    @Test
    void theToggleFollowsTheStateAndWritesTheMark() {
        GodmodeState state = new GodmodeState();
        // Protected right after joining - must not make the first toggle switch godmode off.
        when(player.isInvulnerable()).thenReturn(true);

        try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayer(id)).thenReturn(player);
            GodmodeAction action = new GodmodeAction(IMMEDIATE, state);
            ActionContext context = new ActionContext(Actor.player(id, "Admin", PermissionEvaluator.allowAll()), Source.COMMAND);

            ActionResult<Boolean> on = action.execute(context, null).join();
            assertEquals(Boolean.TRUE, ((ActionResult.Success<Boolean>) on).value());
            verify(player).setInvulnerable(true);
            verify(data).set(GodmodeMarker.KEY, PersistentDataType.BYTE, (byte) 1);
            assertTrue(state.isEnabled(id));

            ActionResult<Boolean> off = action.execute(context, null).join();
            assertEquals(Boolean.FALSE, ((ActionResult.Success<Boolean>) off).value());
            verify(player).setInvulnerable(false);
            verify(data).remove(GodmodeMarker.KEY);
            assertFalse(state.isEnabled(id));
        }
    }
}

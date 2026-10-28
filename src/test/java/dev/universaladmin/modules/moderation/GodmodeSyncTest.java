package dev.universaladmin.modules.moderation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.universaladmin.action.ActionContext;
import dev.universaladmin.action.ActionResult;
import dev.universaladmin.action.Actor;
import dev.universaladmin.action.Source;
import dev.universaladmin.modules.moderation.action.GodmodeAction;
import dev.universaladmin.permission.PermissionEvaluator;
import dev.universaladmin.scheduler.TaskScheduler;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

/** Godmode is the player's invulnerability, which Minecraft keeps across restarts - the state follows it. */
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

    @Test
    void aPlayerWhoComesBackInvulnerableHasGodmodeOn() {
        GodmodeState state = new GodmodeState();
        GodmodeSyncListener listener = new GodmodeSyncListener(state);
        Player player = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(id);
        when(player.isInvulnerable()).thenReturn(true);

        listener.onJoin(new PlayerJoinEvent(player, (Component) null));
        assertTrue(state.isEnabled(id));

        listener.onQuit(new PlayerQuitEvent(player, (Component) null, PlayerQuitEvent.QuitReason.DISCONNECTED));
        assertFalse(state.isEnabled(id));
    }

    @Test
    void theToggleFollowsTheRealInvulnerabilityNotAStaleState() {
        GodmodeState state = new GodmodeState();
        Player player = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(id);
        // Invulnerable from before a restart, while the in-memory state says "off".
        when(player.isInvulnerable()).thenReturn(true);

        try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayer(id)).thenReturn(player);
            ActionResult<Boolean> result = new GodmodeAction(IMMEDIATE, state)
                    .execute(new ActionContext(Actor.player(id, "Admin", PermissionEvaluator.allowAll()), Source.COMMAND), null)
                    .join();

            assertEquals(Boolean.FALSE, ((ActionResult.Success<Boolean>) result).value());
            verify(player).setInvulnerable(false);
            assertFalse(state.isEnabled(id));
        }
    }
}

package dev.universaladmin.modules.moderation;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Reconnect-restore plus "show currently-vanished players to a newly-joined
 * bypass-permission viewer" - pure event-to-service-call translation (see
 * {@link ModerationJoinListener}'s javadoc for the same rule).
 *
 * <p>The persisted-vanish DB check happens in {@link #onPreLogin} (async,
 * before a {@code Player} object even exists - the standard idiom, same as
 * the ban check) so {@link #onJoin} (synchronous, on the main thread) only
 * ever does an in-memory {@link VanishRuntimeState} read, never blocks on IO.
 */
public final class VanishReconnectListener implements Listener {

    private final VanishService vanishService;
    private final VanishRuntimeState runtimeState;

    public VanishReconnectListener(VanishService vanishService, VanishRuntimeState runtimeState) {
        this.vanishService = vanishService;
        this.runtimeState = runtimeState;
    }

    @EventHandler
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED || !vanishService.restoreOnReconnectEnabled()) {
            return;
        }
        if (vanishService.hasPersistedVanish(event.getUniqueId()).join()) {
            runtimeState.setVanished(event.getUniqueId(), true);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission(ModerationPermissions.BYPASS_VANISH.value())) {
            vanishService.revealAllTo(player);
        }
        if (runtimeState.isVanished(player.getUniqueId())) {
            vanishService.apply(player, true);
            if (vanishService.hideJoinMessageEnabled()) {
                event.joinMessage(null);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (runtimeState.isVanished(player.getUniqueId()) && vanishService.hideQuitMessageEnabled()) {
            event.quitMessage(null);
        }
        vanishService.clearRuntimeState(player.getUniqueId());
    }
}

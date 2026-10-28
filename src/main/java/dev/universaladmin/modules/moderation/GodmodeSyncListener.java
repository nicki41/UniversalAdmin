package dev.universaladmin.modules.moderation;

import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

/**
 * Keeps {@link GodmodeState} in step with the godmode a player really has,
 * which survives leaving and restarts: the {@link GodmodeMarker} in the
 * player's data. A marked player gets their invulnerability back on join.
 *
 * <p>Up to 1.7.1 godmode left no mark - only the invulnerability, which
 * Minecraft saves too. Such a player is recognised a few seconds after
 * joining, once the protection every joining player gets has run out: still
 * invulnerable at two checks a second apart, with no hit protection running
 * ({@code getNoDamageTicks() == 0}) and allowed to use godmode, means
 * godmode, and they are marked from then on.
 */
public final class GodmodeSyncListener implements Listener {

    /** The protection after joining lasts three seconds; a hit gives one second at most. */
    private static final long FIRST_CHECK_TICKS = 100L;
    private static final long SECOND_CHECK_TICKS = 30L;

    private final GodmodeState state;
    private final Plugin plugin;

    public GodmodeSyncListener(GodmodeState state, Plugin plugin) {
        this.state = state;
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        boolean marked = GodmodeMarker.marked(player);
        state.setEnabled(player.getUniqueId(), marked);
        if (marked) {
            player.setInvulnerable(true);
            return;
        }
        UUID playerId = player.getUniqueId();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> checkUnmarked(playerId, true), FIRST_CHECK_TICKS);
    }

    private void checkUnmarked(UUID playerId, boolean first) {
        Player player = plugin.getServer().getPlayer(playerId);
        if (player == null || state.isEnabled(playerId) || !player.isInvulnerable() || player.getNoDamageTicks() > 0
                || !player.hasPermission(ModerationPermissions.GODMODE.value())) {
            return;
        }
        if (first) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> checkUnmarked(playerId, false), SECOND_CHECK_TICKS);
            return;
        }
        state.setEnabled(playerId, true);
        GodmodeMarker.mark(player, true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        state.setEnabled(event.getPlayer().getUniqueId(), false);
    }
}

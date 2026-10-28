package dev.universaladmin.modules.moderation;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Keeps {@link GodmodeState} in step with what godmode really is: the
 * player's invulnerability, which Minecraft saves with the player's data.
 * A player who left (or was online during a restart) with godmode on comes
 * back invulnerable - the menu and {@code PlayerStatus} have to say so.
 * Pure event-to-state translation.
 */
public final class GodmodeSyncListener implements Listener {

    private final GodmodeState state;

    public GodmodeSyncListener(GodmodeState state) {
        this.state = state;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        state.setEnabled(player.getUniqueId(), player.isInvulnerable());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        state.setEnabled(event.getPlayer().getUniqueId(), false);
    }
}

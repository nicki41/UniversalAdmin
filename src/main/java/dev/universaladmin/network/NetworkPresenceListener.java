package dev.universaladmin.network;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Tells {@link DefaultNetworkService} who joins and leaves - nothing else. */
public final class NetworkPresenceListener implements Listener {

    private final DefaultNetworkService network;

    public NetworkPresenceListener(DefaultNetworkService network) {
        this.network = network;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        network.playerJoined(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        network.playerLeft(event.getPlayer());
    }
}

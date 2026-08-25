package dev.universaladmin.modules.players;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Translates {@link PlayerJoinEvent}/{@link PlayerQuitEvent} into {@link
 * PlayerService}/{@link PlayerSessionTracker} calls - nothing else, per
 * docs/development/architecture-rules.md's "keine Bukkit-Event-Listener mit Logik" rule and ROADMAP.md's
 * Phase 1 item for this exact listener.
 */
public final class PlayerActivityListener implements Listener {

    private final PlayerService playerService;
    private final PlayerSessionTracker sessionTracker;

    public PlayerActivityListener(PlayerService playerService, PlayerSessionTracker sessionTracker) {
        this.playerService = playerService;
        this.sessionTracker = sessionTracker;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        sessionTracker.recordLogin(event.getPlayer().getUniqueId());
        playerService.getOrCreateProfile(event.getPlayer().getUniqueId(), event.getPlayer().getName());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        sessionTracker.recordLogout(event.getPlayer().getUniqueId());
    }
}

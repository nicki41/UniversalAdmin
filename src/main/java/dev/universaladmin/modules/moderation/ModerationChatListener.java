package dev.universaladmin.modules.moderation;

import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.Optional;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Cancels chat for an actively muted player - pure event-to-service-call
 * translation (see {@link ModerationJoinListener}'s javadoc for the same
 * rule). Uses the modern Paper {@link AsyncChatEvent}, not the deprecated
 * Bukkit {@code AsyncPlayerChatEvent} - it already fires off the main
 * thread, same blocking-{@code join()} idiom as the join check. Reads the
 * database on every message, so a mute from another server of the network
 * applies immediately.
 */
public final class ModerationChatListener implements Listener {

    private final PunishmentService punishmentService;
    private final PunishmentEnforcer enforcer;

    public ModerationChatListener(PunishmentService punishmentService, PunishmentEnforcer enforcer) {
        this.punishmentService = punishmentService;
        this.enforcer = enforcer;
    }

    // ignoreCancelled: a message the Chat module already routed to staff
    // chat (or blocked for chat lock/slow mode) must not additionally
    // trigger a "you are muted" reply.
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        Optional<Punishment> mute = punishmentService.activeMute(player.getUniqueId()).join();
        mute.ifPresent(punishment -> {
            event.setCancelled(true);
            player.sendMessage(enforcer.muteNotice(punishment));
        });
    }
}

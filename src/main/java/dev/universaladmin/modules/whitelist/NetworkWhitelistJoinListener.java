package dev.universaladmin.modules.whitelist;

import dev.universaladmin.localization.ComponentMessages;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.localization.MessageService;
import java.time.Instant;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;

/**
 * Refuses players the network whitelist does not let in - pure
 * event-to-service-call translation (the decision is
 * {@link NetworkWhitelistService#allows}). {@link PlayerLoginEvent} like
 * {@code MaintenanceJoinListener}: the bypass permission needs a real
 * {@code Player}, and the list is answered from memory, so nothing blocks.
 */
@SuppressWarnings("deprecation")
public final class NetworkWhitelistJoinListener implements Listener {

    private final NetworkWhitelistService networkWhitelist;
    private final MessageService messages;

    public NetworkWhitelistJoinListener(NetworkWhitelistService networkWhitelist, MessageService messages) {
        this.networkWhitelist = networkWhitelist;
        this.messages = messages;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onLogin(PlayerLoginEvent event) {
        if (event.getResult() != PlayerLoginEvent.Result.ALLOWED
                || networkWhitelist.allows(event.getPlayer().getUniqueId(), Instant.now())
                || event.getPlayer().hasPermission(WhitelistPermissions.BYPASS_NETWORK.value())) {
            return;
        }
        event.disallow(PlayerLoginEvent.Result.KICK_WHITELIST,
                ComponentMessages.render(messages.get(MessageKey.of("whitelist.enforcement.network"))));
    }
}

package dev.universaladmin.update;

import dev.universaladmin.localization.ComponentMessages;
import dev.universaladmin.permission.PermissionNode;
import java.util.List;
import java.util.function.Supplier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

/**
 * Tells staff who join while an update is pending - the periodic check only
 * reaches whoever is online at that moment. A few seconds after the join,
 * so the notice is not lost among the join messages.
 */
public final class UpdateJoinListener implements Listener {

    private static final long DELAY_TICKS = 60;

    private final Plugin plugin;
    private final Supplier<UpdateCheckService> service;
    private final PermissionNode notifyPermission;

    /** @param service the running update check, {@code null} while there is none */
    public UpdateJoinListener(Plugin plugin, Supplier<UpdateCheckService> service, PermissionNode notifyPermission) {
        this.plugin = plugin;
        this.service = service;
        this.notifyPermission = notifyPermission;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!player.hasPermission(notifyPermission.value())) {
            return;
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            UpdateCheckService checks = service.get();
            if (checks == null || !player.isOnline()) {
                return;
            }
            List<String> notices = checks.pendingNotices();
            notices.forEach(notice -> player.sendMessage(ComponentMessages.render(notice)));
        }, DELAY_TICKS);
    }
}

package dev.universaladmin.notification;

import dev.universaladmin.gui.StatusToggleRegistry;
import dev.universaladmin.localization.ComponentMessages;
import dev.universaladmin.permission.PermissionNode;
import java.time.Duration;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * {@link NotificationService} that delivers notifications as in-game
 * chat/title/actionbar messages. A {@link Notification}'s message is an
 * already-resolved {@link dev.universaladmin.localization.MessageService}
 * string, so it is parsed as MiniMessage here - untrusted parts were already
 * escaped when the message was resolved.
 */
public final class InGameNotificationService implements NotificationService {

    /** Long enough to read an action bar message before the status line comes back. */
    private static final Duration ACTION_BAR_PAUSE = Duration.ofSeconds(3);

    private final StatusToggleRegistry statusLine;

    public InGameNotificationService(StatusToggleRegistry statusLine) {
        this.statusLine = statusLine;
    }

    @Override
    public void notifyPlayer(UUID playerId, Notification notification) {
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            player.sendMessage(render(notification));
        }
    }

    @Override
    public void notifyStaff(PermissionNode requiredPermission, Notification notification) {
        Component rendered = render(notification);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission(requiredPermission.value())) {
                player.sendMessage(rendered);
            }
        }
    }

    @Override
    public void broadcast(Component message) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(message);
        }
    }

    @Override
    public void broadcastTitle(Component title, Component subtitle, Duration fadeIn, Duration stay, Duration fadeOut) {
        Title adventureTitle = Title.title(title, subtitle, Title.Times.times(fadeIn, stay, fadeOut));
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.showTitle(adventureTitle);
        }
    }

    @Override
    public void broadcastActionBar(Component message) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            statusLine.pauseStatusLine(player.getUniqueId(), ACTION_BAR_PAUSE);
            player.sendActionBar(message);
        }
    }

    private Component render(Notification notification) {
        return ComponentMessages.render(notification.message());
    }
}

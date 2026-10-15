package dev.universaladmin.notification;

import dev.universaladmin.permission.PermissionNode;
import java.time.Duration;
import java.util.UUID;
import net.kyori.adventure.text.Component;

/**
 * Delivers a {@link Notification} to a player or to every online staff
 * member with a given permission, plus server-wide broadcast/title/actionbar
 * delivery to every online player. In-game chat/title/actionbar are the only
 * channels today; Discord/web-push channels are future extension territory -
 * see docs/architecture/extensions-future.md - and will implement this same
 * interface rather than replace it.
 *
 * <p>The broadcast methods take an already-rendered {@link Component} rather
 * than a {@link Notification} - callers (e.g. the Server module's broadcast
 * actions) resolve/parse admin-authored MiniMessage text themselves via
 * {@link dev.universaladmin.localization.ComponentMessages}, since a
 * broadcast has no per-recipient title/severity the way a single-player
 * {@link Notification} does.
 */
public interface NotificationService {

    void notifyPlayer(UUID playerId, Notification notification);

    void notifyStaff(PermissionNode requiredPermission, Notification notification);

    /** Sends {@code message} as a chat message to every currently online player. */
    void broadcast(Component message);

    /** Shows a title/subtitle to every currently online player for the given fade-in/stay/fade-out timings. */
    void broadcastTitle(Component title, Component subtitle, Duration fadeIn, Duration stay, Duration fadeOut);

    /** Shows {@code message} in the action bar of every currently online player. */
    void broadcastActionBar(Component message);
}

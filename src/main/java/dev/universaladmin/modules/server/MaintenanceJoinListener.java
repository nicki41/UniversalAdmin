package dev.universaladmin.modules.server;

import dev.universaladmin.localization.ComponentMessages;
import net.kyori.adventure.text.Component;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;

/**
 * Denies login while maintenance mode is enabled, for anyone without {@link
 * ServerPermissions#BYPASS_MAINTENANCE} or an allow-listed name - pure
 * event-to-service-call translation, no business logic here (see docs/development/architecture-rules.md's
 * "keine Bukkit-Event-Listener mit Logik" rule; the actual decision is
 * {@link MaintenanceService#isAllowed}).
 *
 * <p>Uses {@link PlayerLoginEvent} rather than {@code AsyncPlayerPreLoginEvent}
 * (see {@code ModerationJoinListener}'s ban check) on purpose: the bypass
 * check is permission-based, and permissions are only resolved once a real
 * {@code Player} exists - {@code PlayerLoginEvent} fires synchronously on the
 * main thread after that, which is exactly what {@link
 * dev.universaladmin.permission.bukkit.PermissiblePermissionEvaluator}-style
 * checks need. The target Paper API version flags the whole class as
 * deprecated (no public replacement documented for it yet at time of
 * writing) - suppressed rather than chased, since {@code disallow(Result,
 * Component)} is unaffected and there is no alternative synchronous,
 * permission-aware login-gate event to migrate to.
 */
@SuppressWarnings("deprecation")
public final class MaintenanceJoinListener implements Listener {

    private final MaintenanceService maintenanceService;

    public MaintenanceJoinListener(MaintenanceService maintenanceService) {
        this.maintenanceService = maintenanceService;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onLogin(PlayerLoginEvent event) {
        if (event.getResult() != PlayerLoginEvent.Result.ALLOWED) {
            return;
        }
        if (!maintenanceService.current().enabled() || maintenanceService.isAllowed(event.getPlayer())) {
            return;
        }
        Component kickMessage = ComponentMessages.render(maintenanceService.effectiveKickMessage());
        event.disallow(PlayerLoginEvent.Result.KICK_OTHER, kickMessage);
    }
}

package dev.universaladmin.modules.server;

import dev.universaladmin.action.Actor;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.bukkit.entity.Player;

/**
 * Maintenance mode's own read/write service - deliberately not backed by
 * {@link dev.universaladmin.settings.SettingsService}, which is read-only at
 * runtime (a setting only ever changes by editing {@code config.yml} and
 * reloading/restarting). Maintenance mode needs to flip instantly from a GUI
 * click or {@code /admin server maintenance}, so its state lives in
 * {@link MaintenanceStateRepository} instead, with an in-memory cache here
 * for the hot join-check path ({@link #current()} never blocks).
 *
 * <p>{@link dev.universaladmin.settings.CoreSettings#MAINTENANCE_ENABLED}/
 * {@code MAINTENANCE_KICK_MESSAGE} still exist and are used as the boot-time
 * seed (before the persisted state has loaded) and as the default kick
 * message when a state has none of its own - see {@link DefaultMaintenanceService}.
 */
public interface MaintenanceService {

    /** The last known state - served from an in-memory cache, safe to call from any thread including the main thread. */
    MaintenanceState current();

    CompletableFuture<MaintenanceState> enable(String reason, String message, boolean kickNonBypass, Actor actor);

    CompletableFuture<MaintenanceState> disable(Actor actor);

    CompletableFuture<MaintenanceState> setAllowedPlayers(Set<String> playerNames, Actor actor);

    /** {@code true} if {@code player} may join despite maintenance mode being enabled (bypass permission or allow-listed). */
    boolean isAllowed(Player player);

    /** The message to show a blocked player: {@link MaintenanceState#message()} if set, else the configured default. */
    String effectiveKickMessage();
}

package dev.universaladmin.modules.server;

import dev.universaladmin.action.Actor;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.bukkit.entity.Player;

/**
 * Maintenance mode's own read/write service - deliberately not backed by
 * {@link dev.universaladmin.settings.SettingsService}, which is read-only at
 * runtime (a setting only ever changes by editing {@code config.yml} and
 * reloading/restarting). Maintenance mode needs to flip instantly from a GUI
 * click or {@code /admin maintenance}, so its state lives in
 * {@link MaintenanceStateRepository} instead, with an in-memory cache here
 * for the hot join-check path (nothing here that answers synchronously
 * ever blocks).
 *
 * <p>Two {@linkplain MaintenanceScope scopes}, each with its own reason,
 * message and allow-list: this server, and - in network mode - the whole
 * network. A player is refused if either refuses them; the network's
 * maintenance is checked first, so its message wins.
 *
 * <p>{@link dev.universaladmin.settings.CoreSettings#MAINTENANCE_ENABLED}/
 * {@code MAINTENANCE_KICK_MESSAGE} still exist and are used as the boot-time
 * seed (before the persisted state has loaded) and as the default kick
 * message when a state has none of its own - see {@link DefaultMaintenanceService}.
 */
public interface MaintenanceService {

    /** This server's own state - from the in-memory cache, safe on any thread including the main thread. */
    default MaintenanceState current() {
        return current(MaintenanceScope.SERVER);
    }

    /** The cached state of {@code scope}; {@link MaintenanceScope#NETWORK} is always disabled without network mode. */
    MaintenanceState current(MaintenanceScope scope);

    /** Whether {@link MaintenanceScope#NETWORK} can be used here (network mode is on). */
    boolean networkAvailable();

    /** Whether this server currently refuses players because of maintenance - its own or the network's. */
    boolean active();

    /**
     * Enables maintenance for {@code scope}. With {@code kickNonBypass}, every
     * player online who may not stay is removed: to the proxy's fallback
     * server for this server's maintenance, from the whole network for the
     * network's (on every server).
     */
    CompletableFuture<MaintenanceState> enable(
            MaintenanceScope scope, String reason, String message, boolean kickNonBypass, Actor actor);

    CompletableFuture<MaintenanceState> disable(MaintenanceScope scope, Actor actor);

    CompletableFuture<MaintenanceState> setAllowedPlayers(MaintenanceScope scope, Set<String> playerNames, Actor actor);

    /** The scope whose maintenance refuses {@code player} (no bypass permission, not allow-listed), if any. */
    Optional<MaintenanceScope> blockedBy(Player player);

    /** {@code true} if {@code player} may join despite maintenance mode - see {@link #blockedBy}. */
    default boolean isAllowed(Player player) {
        return blockedBy(player).isEmpty();
    }

    /** The message for a player refused by {@code scope}: its {@link MaintenanceState#message()} if set, else the configured default. */
    String kickMessage(MaintenanceScope scope);
}

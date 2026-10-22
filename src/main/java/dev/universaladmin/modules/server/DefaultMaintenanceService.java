package dev.universaladmin.modules.server;

import dev.universaladmin.action.Actor;
import dev.universaladmin.localization.ComponentMessages;
import dev.universaladmin.network.NetworkListener;
import dev.universaladmin.network.NetworkMessage;
import dev.universaladmin.network.NetworkService;
import dev.universaladmin.network.ScopeKeys;
import dev.universaladmin.scheduler.TaskScheduler;
import dev.universaladmin.settings.CoreSettings;
import dev.universaladmin.settings.SettingsService;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Maintenance per scope key: {@code local} without network mode,
 * {@code server:<name>} for this server in network mode and {@code network}
 * for the whole network. A network server without a row of its own yet
 * (first start after the upgrade from the single shared row) reads
 * {@code local}, the copied old row, until it saves its own.
 *
 * <p>The network's state changes on another server arrive as a
 * {@link #NETWORK_TOPIC} message (and, as a fallback, with
 * {@link #refresh()}, which the module runs every few seconds in network
 * mode). An unnamed server is called {@code server-<port>} until the proxy
 * tells its real name after the first join; {@link #refresh()} then loads
 * the state for the new name and removes players that state refuses.
 */
public final class DefaultMaintenanceService implements MaintenanceService, NetworkListener {

    /** Network channel topic: the network's maintenance changed - reload it, and with {@code kick=true} remove refused players. */
    public static final String NETWORK_TOPIC = "server.maintenance";
    static final String LOCAL_SCOPE = ScopeKeys.LOCAL;
    static final String NETWORK_SCOPE = ScopeKeys.NETWORK;

    private final MaintenanceStateRepository repository;
    private final SettingsService settings;
    private final TaskScheduler scheduler;
    private final Logger logger;
    private final NetworkService network;
    /** Bumped before and after every save, so a refresh that overlapped one does not overwrite the saved state with an older read. */
    private final AtomicLong writes = new AtomicLong();

    private volatile MaintenanceState server;
    private volatile MaintenanceState networkWide = MaintenanceState.disabled();
    private volatile String loadedServerKey;

    public DefaultMaintenanceService(
            MaintenanceStateRepository repository, SettingsService settings, TaskScheduler scheduler, Logger logger,
            NetworkService network) {
        this.repository = repository;
        this.settings = settings;
        this.scheduler = scheduler;
        this.logger = logger;
        this.network = network;
        // Seeds synchronously from config.yml's defaults so current()/blockedBy()
        // are correct even in the brief window before the async DB load below
        // completes - see the class javadoc.
        this.server = new MaintenanceState(
                settings.get(CoreSettings.MAINTENANCE_ENABLED), null, settings.get(CoreSettings.MAINTENANCE_KICK_MESSAGE),
                Set.of(), Instant.now(), "startup-default");
        refresh().exceptionally(error -> {
            logger.log(Level.WARNING, "Failed to load persisted maintenance state; keeping the config.yml default.", error);
            return null;
        });
    }

    /** Reloads both scopes from storage (asynchronously); the cache keeps its values if the load fails. */
    public CompletableFuture<Void> refresh() {
        String key = serverKey();
        boolean networkMode = network.enabled();
        long generation = writes.get();
        Set<String> keys = networkMode ? Set.of(key, NETWORK_SCOPE, LOCAL_SCOPE) : Set.of(LOCAL_SCOPE);
        return repository.load(keys).thenAccept(rows -> {
            if (writes.get() != generation) {
                return;
            }
            String previousKey = loadedServerKey;
            MaintenanceState own = rows.containsKey(key) ? rows.get(key) : rows.get(LOCAL_SCOPE);
            if (own != null) {
                server = own;
            }
            if (networkMode) {
                networkWide = rows.getOrDefault(NETWORK_SCOPE, MaintenanceState.disabled());
            }
            loadedServerKey = key;
            if (previousKey != null && !previousKey.equals(key) && server.enabled()) {
                // Players joined under the name this server had before the proxy told the real one.
                kickBlocked();
            }
        });
    }

    @Override
    public void onMessage(NetworkMessage message) {
        boolean kick = Boolean.parseBoolean(message.get("kick", "false"));
        refresh().thenRun(() -> {
            if (kick) {
                kickBlocked();
            }
        }).exceptionally(error -> {
            logger.log(Level.WARNING, "Failed to reload the network maintenance state after a change on " + message.source(), error);
            return null;
        });
    }

    @Override
    public MaintenanceState current(MaintenanceScope scope) {
        if (scope == MaintenanceScope.NETWORK) {
            return network.enabled() ? networkWide : MaintenanceState.disabled();
        }
        return server;
    }

    @Override
    public boolean networkAvailable() {
        return network.enabled();
    }

    @Override
    public boolean active() {
        return server.enabled() || current(MaintenanceScope.NETWORK).enabled();
    }

    @Override
    public CompletableFuture<MaintenanceState> enable(
            MaintenanceScope scope, String reason, String message, boolean kickNonBypass, Actor actor) {
        MaintenanceState state = new MaintenanceState(
                true, reason, message, current(scope).allowedPlayers(), Instant.now(), actor.displayName());
        return persist(scope, state, kickNonBypass).thenApply(saved -> {
            if (kickNonBypass) {
                kickBlocked();
            }
            return saved;
        });
    }

    @Override
    public CompletableFuture<MaintenanceState> disable(MaintenanceScope scope, Actor actor) {
        MaintenanceState previous = current(scope);
        MaintenanceState state = new MaintenanceState(
                false, previous.reason(), previous.message(), previous.allowedPlayers(), Instant.now(), actor.displayName());
        return persist(scope, state, false);
    }

    @Override
    public CompletableFuture<MaintenanceState> setAllowedPlayers(MaintenanceScope scope, Set<String> playerNames, Actor actor) {
        Set<String> normalized = playerNames.stream().map(name -> name.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
        MaintenanceState previous = current(scope);
        MaintenanceState state = new MaintenanceState(
                previous.enabled(), previous.reason(), previous.message(), normalized, Instant.now(), actor.displayName());
        return persist(scope, state, false);
    }

    @Override
    public Optional<MaintenanceScope> blockedBy(Player player) {
        MaintenanceState networkNow = current(MaintenanceScope.NETWORK);
        MaintenanceState serverNow = server;
        if (!networkNow.enabled() && !serverNow.enabled()) {
            return Optional.empty();
        }
        if (player.hasPermission(ServerPermissions.BYPASS_MAINTENANCE.value())) {
            return Optional.empty();
        }
        if (networkNow.enabled() && !networkNow.isAllowedByName(player.getName())) {
            return Optional.of(MaintenanceScope.NETWORK);
        }
        if (serverNow.enabled() && !serverNow.isAllowedByName(player.getName())) {
            return Optional.of(MaintenanceScope.SERVER);
        }
        return Optional.empty();
    }

    @Override
    public String kickMessage(MaintenanceScope scope) {
        String message = current(scope).message();
        return message != null && !message.isBlank() ? message : settings.get(CoreSettings.MAINTENANCE_KICK_MESSAGE);
    }

    private CompletableFuture<MaintenanceState> persist(MaintenanceScope scope, MaintenanceState state, boolean kick) {
        if (scope == MaintenanceScope.NETWORK && !network.enabled()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Network maintenance needs network mode"));
        }
        writes.incrementAndGet();
        return repository.save(key(scope), state).thenApply(ignored -> {
            if (scope == MaintenanceScope.NETWORK) {
                networkWide = state;
                network.publish(NETWORK_TOPIC, Map.of("kick", Boolean.toString(kick)));
            } else {
                server = state;
            }
            writes.incrementAndGet();
            return state;
        });
    }

    private String serverKey() {
        return ScopeKeys.server(network);
    }

    private String key(MaintenanceScope scope) {
        return scope == MaintenanceScope.NETWORK ? NETWORK_SCOPE : serverKey();
    }

    /** Removes every online player maintenance refuses: from the network for its maintenance, to the fallback server for this server's. */
    private void kickBlocked() {
        scheduler.runOnMainThread(() -> {
            for (Player player : List.copyOf(Bukkit.getOnlinePlayers())) {
                blockedBy(player).ifPresent(scope -> {
                    Component reason = ComponentMessages.render(kickMessage(scope));
                    if (scope == MaintenanceScope.NETWORK) {
                        network.disconnect(player, reason);
                    } else {
                        player.kick(reason);
                    }
                });
            }
        });
    }
}

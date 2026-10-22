package dev.universaladmin.network;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import dev.universaladmin.network.NetworkRepository.MessageRow;
import dev.universaladmin.network.NetworkRepository.PlayerRow;
import dev.universaladmin.network.NetworkRepository.ServerRow;
import dev.universaladmin.scheduler.TaskScheduler;
import java.lang.reflect.Type;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * The {@link NetworkService}. With network mode on, it
 * <ul>
 *   <li>writes this server's heartbeat (players, TPS, version) every
 *       {@link #HEARTBEAT_TICKS} and keeps an in-memory snapshot of every
 *       server and player, refreshed just as often - {@link #servers()} and
 *       {@link #players()} never touch the database;</li>
 *   <li>fetches messages from the other servers every
 *       {@code network.poll-interval} and hands them to the subscribed
 *       listeners on the main thread;</li>
 *   <li>talks to the proxy through {@link ProxyChannel}.</li>
 * </ul>
 * With network mode off every method still works and describes just this
 * server, so callers never branch on it.
 *
 * <p>Message ids come from an auto-increment column. Two servers inserting
 * at the same time can commit out of id order, so each poll looks back
 * {@link #ID_LOOKBACK} ids and skips the ones it already delivered.
 */
public final class DefaultNetworkService implements NetworkService {

    static final String ARRIVAL_TOPIC = "core.arrival";

    private static final long HEARTBEAT_TICKS = 100L;
    private static final long PRUNE_TICKS = 20L * 60;
    private static final long PROXY_LIST_TICKS = 20L * 30;
    private static final Duration SERVER_TIMEOUT = Duration.ofSeconds(20);
    private static final Duration SERVER_FORGET = Duration.ofMinutes(10);
    private static final Duration MESSAGE_LIFETIME = Duration.ofMinutes(5);
    private static final Duration ARRIVAL_LIFETIME = Duration.ofMinutes(1);
    private static final long ID_LOOKBACK = 200L;
    private static final int POLL_LIMIT = 500;
    private static final int DELIVERED_MEMORY = 2000;
    private static final Type PAYLOAD_TYPE = new TypeToken<Map<String, String>>() { }.getType();

    private final Plugin plugin;
    private final NetworkRepository repository;
    private final TaskScheduler scheduler;
    private final Logger logger;
    private final String version;
    private final boolean enabled;
    private final Duration pollInterval;
    private final boolean listOtherServers;
    private final Instant startedAt = Instant.now();
    private final Gson gson = new Gson();
    private final ProxyChannel proxy;
    private final Map<String, List<NetworkListener>> listeners = new ConcurrentHashMap<>();
    private final Map<UUID, Arrival> arrivals = new ConcurrentHashMap<>();
    private final Map<UUID, Instant> joinedAt = new ConcurrentHashMap<>();
    private final Set<Long> delivered = new LinkedHashSet<>();
    private final List<BukkitTask> tasks = new ArrayList<>();

    private volatile String serverName;
    private volatile boolean nameConfirmed;
    private volatile List<NetworkServer> serverSnapshot = List.of();
    private volatile List<NetworkPlayer> playerSnapshot = List.of();
    private volatile Map<String, List<String>> proxySnapshot = Map.of();
    private volatile long lastMessageId = -1;
    private volatile boolean polling;

    private record Arrival(ArrivalTarget target, Instant expiresAt) {
    }

    /**
     * @param enabled          network mode requested and a shared MySQL/MariaDB database in use
     * @param configuredName   {@code network.server-name}; empty = ask the proxy once a player joins
     */
    public DefaultNetworkService(
            Plugin plugin, NetworkRepository repository, TaskScheduler scheduler, Logger logger, String version,
            boolean enabled, String configuredName, Duration pollInterval, boolean listOtherServers) {
        this.plugin = plugin;
        this.repository = repository;
        this.scheduler = scheduler;
        this.logger = logger;
        this.version = version;
        this.enabled = enabled;
        this.pollInterval = pollInterval;
        this.listOtherServers = listOtherServers;
        this.proxy = new ProxyChannel(plugin, logger);
        boolean named = configuredName != null && !configuredName.isBlank();
        this.serverName = named ? configuredName : "server-" + plugin.getServer().getPort();
        this.nameConfirmed = named;
    }

    /** Starts the heartbeat, the message polling and the proxy channel. */
    public void start() {
        proxy.register();
        if (!enabled) {
            return;
        }
        if (!proxy.behindProxy()) {
            logger.warning("Network mode is on, but this server is not set up for a proxy (spigot.yml bungeecord / "
                    + "paper-global.yml velocity forwarding) - moving players between servers will not work.");
        }
        logger.info(() -> "Network mode: this server is '" + serverName + "'"
                + (nameConfirmed ? "." : " until the proxy tells its real name (set network.server-name to skip this)."));
        if (plugin.getServer().getPluginManager().getPlugin("LuckPerms") == null) {
            // OP and permissions.yml are per server - the most common surprise in a new network.
            logger.info("Network mode: OP and permissions count per server. To give staff the same rights on every server "
                    + "and on the proxy, use a permissions plugin such as LuckPerms with one shared database - see "
                    + "docs/user/network.md, \"Permissions\".");
        }
        repository.servers().thenAccept(rows -> rows.stream()
                .filter(row -> row.name().equals(serverName) && row.lastSeen().isAfter(Instant.now().minus(SERVER_TIMEOUT)))
                .findFirst()
                .ifPresent(row -> logger.warning("Another server already reports itself as '" + serverName
                        + "' - give every server its own network.server-name.")));
        repository.latestMessageId().whenComplete((id, error) -> {
            if (error != null) {
                logger.log(Level.WARNING, "Network mode: could not read the message channel - retrying with the next poll", error);
                lastMessageId = 0;
            } else {
                lastMessageId = id;
            }
        });
        subscribe(ARRIVAL_TOPIC, this::onArrivalMessage);

        long pollTicks = Math.max(5L, pollInterval.toMillis() / 50L);
        tasks.add(repeat(this::heartbeat, 1L, HEARTBEAT_TICKS));
        tasks.add(repeat(this::refresh, 20L, HEARTBEAT_TICKS));
        tasks.add(repeat(this::poll, 40L, pollTicks));
        tasks.add(repeat(this::prune, PRUNE_TICKS, PRUNE_TICKS));
        if (listOtherServers) {
            tasks.add(repeat(this::refreshProxyList, 100L, PROXY_LIST_TICKS));
        }
    }

    /** Stops everything and removes this server from the network list. */
    public void stop() {
        tasks.forEach(BukkitTask::cancel);
        tasks.clear();
        proxy.unregister();
        if (enabled) {
            try {
                repository.removeServer(serverName).get(3, java.util.concurrent.TimeUnit.SECONDS);
            } catch (Exception e) {
                logger.log(Level.FINE, "Network mode: could not remove this server on shutdown", e);
            }
        }
    }

    private BukkitTask repeat(Runnable task, long delay, long period) {
        return plugin.getServer().getScheduler().runTaskTimer(plugin, task, delay, period);
    }

    @Override
    public boolean enabled() {
        return enabled;
    }

    @Override
    public String serverName() {
        return serverName;
    }

    @Override
    public List<NetworkServer> servers() {
        if (!enabled) {
            return List.of(localServer());
        }
        List<NetworkServer> servers = new ArrayList<>(serverSnapshot);
        Set<String> known = new java.util.HashSet<>();
        servers.forEach(server -> known.add(server.name().toLowerCase(Locale.ROOT)));
        proxySnapshot.forEach((name, players) -> {
            if (!known.contains(name.toLowerCase(Locale.ROOT))) {
                servers.add(new NetworkServer(name, false, true, players.size(), 0, 0, null, null));
            }
        });
        servers.sort(Comparator.comparing(NetworkServer::name, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(servers);
    }

    @Override
    public List<NetworkPlayer> players() {
        if (!enabled) {
            return localPlayers();
        }
        List<NetworkPlayer> players = new ArrayList<>(playerSnapshot);
        Set<String> uaServers = new java.util.HashSet<>();
        serverSnapshot.forEach(server -> uaServers.add(server.name().toLowerCase(Locale.ROOT)));
        proxySnapshot.forEach((server, names) -> {
            if (!uaServers.contains(server.toLowerCase(Locale.ROOT))) {
                names.forEach(name -> players.add(new NetworkPlayer(null, name, server, null)));
            }
        });
        players.sort(Comparator.comparing(NetworkPlayer::name, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(players);
    }

    @Override
    public Optional<NetworkPlayer> player(UUID playerId) {
        Player local = Bukkit.getPlayer(playerId);
        if (local != null) {
            return Optional.of(new NetworkPlayer(local.getUniqueId(), local.getName(), serverName, joinedAt.get(local.getUniqueId())));
        }
        return enabled ? playerSnapshot.stream().filter(player -> playerId.equals(player.id())).findFirst() : Optional.empty();
    }

    @Override
    public Optional<NetworkPlayer> player(String name) {
        Player local = Bukkit.getPlayerExact(name);
        if (local != null) {
            return player(local.getUniqueId());
        }
        return enabled ? players().stream().filter(player -> player.name().equalsIgnoreCase(name)).findFirst() : Optional.empty();
    }

    @Override
    public void publish(String topic, Map<String, String> payload) {
        publish(topic, payload, null);
    }

    @Override
    public void publish(String topic, Map<String, String> payload, String server) {
        if (!enabled) {
            return;
        }
        repository.publish(serverName, server, topic, gson.toJson(payload), Instant.now()).exceptionally(error -> {
            logger.log(Level.WARNING, "Network mode: could not send '" + topic + "' to the other servers", error);
            return null;
        });
    }

    @Override
    public void subscribe(String topic, NetworkListener listener) {
        listeners.computeIfAbsent(topic, ignored -> new CopyOnWriteArrayList<>()).add(listener);
    }

    @Override
    public void unsubscribe(String topic, NetworkListener listener) {
        List<NetworkListener> list = listeners.get(topic);
        if (list != null) {
            list.remove(listener);
        }
    }

    @Override
    public CompletableFuture<Boolean> connect(Player player, String server) {
        if (!proxy.behindProxy()) {
            return CompletableFuture.completedFuture(false);
        }
        return scheduler.supplyOnMainThread(() -> {
            proxy.connect(player, server);
            return true;
        });
    }

    @Override
    public CompletableFuture<Boolean> connectOther(Player carrier, String playerName, String server) {
        if (!proxy.behindProxy()) {
            return CompletableFuture.completedFuture(false);
        }
        return scheduler.supplyOnMainThread(() -> {
            proxy.connectOther(carrier, playerName, server);
            return true;
        });
    }

    @Override
    public void disconnect(Player player, Component reason) {
        scheduler.runOnMainThread(() -> {
            if (!player.isOnline()) {
                return;
            }
            if (!proxy.behindProxy()) {
                player.kick(reason);
                return;
            }
            proxy.kick(player, reason);
            // The proxy normally closes the connection right away; if it
            // ignored the request (old proxy, channel disabled), this
            // server's own kick still makes sure the player is gone.
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    player.kick(reason);
                }
            }, 20L);
        });
    }

    @Override
    public CompletableFuture<Boolean> sendTo(Player player, String server, ArrivalTarget target) {
        if (server.equalsIgnoreCase(serverName)) {
            return scheduler.supplyOnMainThread(() -> {
                expectArrival(player.getUniqueId(), target);
                applyArrival(player);
                return true;
            });
        }
        if (!proxy.behindProxy()) {
            return CompletableFuture.completedFuture(false);
        }
        announceArrival(player.getUniqueId(), target, server);
        return connect(player, server);
    }

    @Override
    public CompletableFuture<Boolean> bring(Player destination, NetworkPlayer target) {
        if (target.server().equalsIgnoreCase(serverName) && target.id() != null) {
            return scheduler.supplyOnMainThread(() -> {
                Player local = Bukkit.getPlayer(target.id());
                if (local == null) {
                    return false;
                }
                local.teleportAsync(destination.getLocation());
                return true;
            });
        }
        if (!proxy.behindProxy()) {
            return CompletableFuture.completedFuture(false);
        }
        if (target.id() != null) {
            expectArrival(target.id(), new ArrivalTarget.ToPlayer(destination.getUniqueId()));
        }
        return connectOther(destination, target.name(), serverName);
    }

    /** Remembers that {@code playerId}, about to join this server, should be teleported to {@code target}. */
    private void expectArrival(UUID playerId, ArrivalTarget target) {
        arrivals.put(playerId, new Arrival(target, Instant.now().plus(ARRIVAL_LIFETIME)));
        // Already here (the message was slower than the player)? Then now.
        scheduler.runOnMainThread(() -> {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                applyArrival(player);
            }
        });
    }

    /** Tells {@code server} to expect {@code playerId} - the sending side of {@link #expectArrival}. */
    private void announceArrival(UUID playerId, ArrivalTarget target, String server) {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("player", playerId.toString());
        switch (target) {
            case ArrivalTarget.ToPlayer toPlayer -> payload.put("to-player", toPlayer.playerId().toString());
            case ArrivalTarget.ToLocation location -> {
                payload.put("world", location.world());
                payload.put("x", Double.toString(location.x()));
                payload.put("y", Double.toString(location.y()));
                payload.put("z", Double.toString(location.z()));
            }
        }
        publish(ARRIVAL_TOPIC, payload, server);
    }

    private void onArrivalMessage(NetworkMessage message) {
        UUID playerId = UUID.fromString(message.get("player", ""));
        String toPlayer = message.get("to-player", null);
        ArrivalTarget target = toPlayer != null
                ? new ArrivalTarget.ToPlayer(UUID.fromString(toPlayer))
                : new ArrivalTarget.ToLocation(message.get("world", ""), Double.parseDouble(message.get("x", "0")),
                        Double.parseDouble(message.get("y", "0")), Double.parseDouble(message.get("z", "0")));
        expectArrival(playerId, target);
    }

    /** Called by {@link NetworkPresenceListener} - main thread. */
    void playerJoined(Player player) {
        Instant now = Instant.now();
        joinedAt.put(player.getUniqueId(), now);
        // The arrival teleport needs the player fully in the world - next tick.
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> applyArrival(player), 5L);
        if (!enabled) {
            return;
        }
        repository.playerJoined(new PlayerRow(player.getUniqueId(), player.getName(), serverName, now));
        if (!nameConfirmed && proxy.behindProxy()) {
            proxy.serverName(player).thenAccept(this::adoptProxyName).exceptionally(error -> null);
        }
    }

    /** Called by {@link NetworkPresenceListener} - main thread. */
    void playerLeft(Player player) {
        joinedAt.remove(player.getUniqueId());
        if (enabled) {
            repository.playerLeft(player.getUniqueId(), serverName);
        }
    }

    private void adoptProxyName(String name) {
        if (nameConfirmed || name == null || name.isBlank() || name.equals(serverName)) {
            nameConfirmed = true;
            return;
        }
        String previous = serverName;
        serverName = name;
        nameConfirmed = true;
        repository.removeServer(previous);
        logger.info(() -> "Network mode: the proxy calls this server '" + name + "' - using that name "
                + "(set network.server-name: " + name + " to skip the lookup).");
        scheduler.runOnMainThread(this::heartbeat);
    }

    private void applyArrival(Player player) {
        Arrival arrival = arrivals.remove(player.getUniqueId());
        if (arrival == null || arrival.expiresAt().isBefore(Instant.now()) || !player.isOnline()) {
            return;
        }
        Location destination = switch (arrival.target()) {
            case ArrivalTarget.ToPlayer toPlayer -> {
                Player other = Bukkit.getPlayer(toPlayer.playerId());
                yield other == null ? null : other.getLocation();
            }
            case ArrivalTarget.ToLocation location -> {
                World world = Bukkit.getWorld(location.world());
                yield world == null ? null : new Location(world, location.x(), location.y(), location.z());
            }
        };
        if (destination != null) {
            player.teleportAsync(destination);
        }
    }

    private void heartbeat() {
        List<PlayerRow> players = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            players.add(new PlayerRow(player.getUniqueId(), player.getName(), serverName,
                    joinedAt.getOrDefault(player.getUniqueId(), Instant.now())));
        }
        ServerRow row = new ServerRow(serverName, players.size(), Bukkit.getMaxPlayers(), currentTps(), version, startedAt, Instant.now());
        repository.heartbeat(row, players).exceptionally(error -> {
            logger.log(Level.WARNING, "Network mode: heartbeat failed", error);
            return null;
        });
    }

    private void refresh() {
        repository.servers().thenCombine(repository.players(), this::snapshot).exceptionally(error -> {
            logger.log(Level.FINE, "Network mode: could not refresh the server list", error);
            return null;
        });
    }

    private Void snapshot(List<ServerRow> serverRows, List<PlayerRow> playerRows) {
        Instant now = Instant.now();
        List<NetworkServer> servers = new ArrayList<>();
        Set<String> online = new java.util.HashSet<>();
        for (ServerRow row : serverRows) {
            if (row.lastSeen().isBefore(now.minus(SERVER_FORGET))) {
                continue;
            }
            boolean up = row.lastSeen().isAfter(now.minus(SERVER_TIMEOUT));
            if (up) {
                online.add(row.name());
            }
            servers.add(new NetworkServer(row.name(), true, up, up ? row.onlinePlayers() : 0, row.maxPlayers(), row.tps(),
                    row.version(), row.lastSeen()));
        }
        List<NetworkPlayer> players = playerRows.stream()
                .filter(row -> online.contains(row.server()))
                .map(row -> new NetworkPlayer(row.playerId(), row.name(), row.server(), row.joinedAt()))
                .toList();
        serverSnapshot = List.copyOf(servers);
        playerSnapshot = players;
        return null;
    }

    private void refreshProxyList() {
        Player carrier = Bukkit.getOnlinePlayers().stream().findFirst().orElse(null);
        if (carrier == null || !proxy.behindProxy()) {
            return;
        }
        proxy.servers(carrier).thenCompose(names -> {
            Map<String, List<String>> result = new ConcurrentHashMap<>();
            List<CompletableFuture<Void>> lookups = new ArrayList<>();
            for (String name : names) {
                lookups.add(proxy.players(carrier, name).thenAccept(players -> result.put(name, players))
                        .exceptionally(error -> {
                            result.put(name, List.of());
                            return null;
                        }));
            }
            return CompletableFuture.allOf(lookups.toArray(CompletableFuture[]::new)).thenApply(ignored -> result);
        }).thenAccept(result -> proxySnapshot = Map.copyOf(result)).exceptionally(error -> null);
    }

    private void poll() {
        if (polling || lastMessageId < 0) {
            return;
        }
        polling = true;
        long from = Math.max(0, lastMessageId - ID_LOOKBACK);
        repository.messagesSince(from, serverName, POLL_LIMIT).whenComplete((rows, error) -> {
            polling = false;
            if (error != null) {
                logger.log(Level.FINE, "Network mode: could not fetch messages", error);
                return;
            }
            List<MessageRow> fresh = new ArrayList<>();
            synchronized (delivered) {
                for (MessageRow row : rows) {
                    if (delivered.add(row.id())) {
                        fresh.add(row);
                    }
                    lastMessageId = Math.max(lastMessageId, row.id());
                }
                while (delivered.size() > DELIVERED_MEMORY) {
                    delivered.remove(delivered.iterator().next());
                }
            }
            if (!fresh.isEmpty()) {
                scheduler.runOnMainThread(() -> fresh.forEach(this::dispatch));
            }
        });
    }

    private void dispatch(MessageRow row) {
        List<NetworkListener> subscribers = listeners.get(row.topic());
        if (subscribers == null || subscribers.isEmpty()) {
            return;
        }
        Map<String, String> payload;
        try {
            Map<String, String> parsed = gson.fromJson(row.payload(), PAYLOAD_TYPE);
            payload = parsed == null ? Map.of() : parsed;
        } catch (RuntimeException e) {
            logger.log(Level.WARNING, "Network mode: ignoring an unreadable '" + row.topic() + "' message from " + row.source(), e);
            return;
        }
        NetworkMessage message = new NetworkMessage(row.topic(), row.source(), payload, row.createdAt());
        for (NetworkListener listener : subscribers) {
            try {
                listener.onMessage(message);
            } catch (RuntimeException e) {
                logger.log(Level.SEVERE, "Network mode: a listener for '" + row.topic() + "' failed", e);
            }
        }
    }

    private void prune() {
        repository.pruneMessages(Instant.now().minus(MESSAGE_LIFETIME)).exceptionally(error -> null);
        arrivals.values().removeIf(arrival -> arrival.expiresAt().isBefore(Instant.now()));
    }

    private NetworkServer localServer() {
        return new NetworkServer(serverName, true, true, Bukkit.getOnlinePlayers().size(), Bukkit.getMaxPlayers(),
                currentTps(), version, Instant.now());
    }

    private List<NetworkPlayer> localPlayers() {
        Collection<? extends Player> online = Bukkit.getOnlinePlayers();
        List<NetworkPlayer> players = new ArrayList<>(online.size());
        for (Player player : online) {
            players.add(new NetworkPlayer(player.getUniqueId(), player.getName(), serverName, joinedAt.get(player.getUniqueId())));
        }
        players.sort(Comparator.comparing(NetworkPlayer::name, String.CASE_INSENSITIVE_ORDER));
        return players;
    }

    private static double currentTps() {
        double[] tps = Bukkit.getTPS();
        return tps.length == 0 ? 20.0 : Math.min(20.0, tps[0]);
    }
}

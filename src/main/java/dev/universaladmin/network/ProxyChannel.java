package dev.universaladmin.network;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

/**
 * Talks to the proxy over the standard BungeeCord plugin messaging channel
 * ({@code bungeecord:main}), which BungeeCord and Velocity both implement
 * (Velocity: {@code bungee-plugin-message-channel = true}, its default).
 * Every message travels through a player's connection - the "carrier" - so
 * nothing can be sent while this server is empty.
 *
 * <p>Requests with an answer (GetServer, GetServers, PlayerList) complete
 * their future when the proxy replies, or exceptionally after a few
 * seconds. Main thread only for sending.
 */
final class ProxyChannel implements PluginMessageListener {

    static final String CHANNEL = "BungeeCord";
    private static final long REPLY_TIMEOUT_SECONDS = 3;

    private final Plugin plugin;
    private final Logger logger;
    private final boolean behindProxy;
    private final Map<String, Deque<CompletableFuture<String[]>>> pending = new HashMap<>();

    ProxyChannel(Plugin plugin, Logger logger) {
        this.plugin = plugin;
        this.logger = logger;
        this.behindProxy = detectProxy();
    }

    /** Whether this server is configured for a proxy (BungeeCord mode or Velocity forwarding). */
    boolean behindProxy() {
        return behindProxy;
    }

    void register() {
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, CHANNEL, this);
    }

    void unregister() {
        plugin.getServer().getMessenger().unregisterOutgoingPluginChannel(plugin, CHANNEL);
        plugin.getServer().getMessenger().unregisterIncomingPluginChannel(plugin, CHANNEL, this);
        synchronized (pending) {
            pending.values().forEach(futures -> futures.forEach(future -> future.cancel(false)));
            pending.clear();
        }
    }

    /** Sends {@code player} to {@code server}. */
    void connect(Player player, String server) {
        send(player, "Connect", server);
    }

    /** Sends the player named {@code playerName} (on any server) to {@code server}. */
    void connectOther(Player carrier, String playerName, String server) {
        send(carrier, "ConnectOther", playerName, server);
    }

    /** Disconnects {@code player} from the proxy itself, with {@code reason}. */
    void kick(Player player, Component reason) {
        send(player, "KickPlayerRaw", player.getName(), GsonComponentSerializer.gson().serialize(reason));
    }

    /** The name the proxy knows this server by. */
    CompletableFuture<String> serverName(Player carrier) {
        return request(carrier, "GetServer", "", new String[] {"GetServer"}).thenApply(reply -> reply[0]);
    }

    /** Every server registered at the proxy. */
    CompletableFuture<List<String>> servers(Player carrier) {
        return request(carrier, "GetServers", "", new String[] {"GetServers"}).thenApply(reply -> split(reply[0]));
    }

    /** The players on {@code server} (names only - that is all the proxy says). */
    CompletableFuture<List<String>> players(Player carrier, String server) {
        return request(carrier, "PlayerList", server, new String[] {"PlayerList", server}).thenApply(reply -> split(reply[0]));
    }

    private CompletableFuture<String[]> request(Player carrier, String subchannel, String key, String[] message) {
        CompletableFuture<String[]> future = new CompletableFuture<>();
        synchronized (pending) {
            pending.computeIfAbsent(subchannel + ":" + key, ignored -> new ArrayDeque<>()).add(future);
        }
        send(carrier, message);
        return future.orTimeout(REPLY_TIMEOUT_SECONDS, TimeUnit.SECONDS).whenComplete((reply, error) -> {
            if (error != null) {
                synchronized (pending) {
                    Deque<CompletableFuture<String[]>> queue = pending.get(subchannel + ":" + key);
                    if (queue != null) {
                        queue.remove(future);
                    }
                }
            }
        });
    }

    private void send(Player carrier, String... parts) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            for (String part : parts) {
                out.writeUTF(part);
            }
        } catch (IOException impossible) {
            throw new IllegalStateException(impossible);
        }
        carrier.sendPluginMessage(plugin, CHANNEL, bytes.toByteArray());
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!CHANNEL.equals(channel) && !"bungeecord:main".equals(channel)) {
            return;
        }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(message))) {
            String subchannel = in.readUTF();
            switch (subchannel) {
                case "GetServer", "GetServers" -> complete(subchannel + ":", new String[] {in.readUTF()});
                case "PlayerList" -> {
                    String server = in.readUTF();
                    complete(subchannel + ":" + server, new String[] {in.readUTF()});
                }
                default -> {
                    // other plugins' traffic on the shared channel
                }
            }
        } catch (IOException e) {
            logger.log(Level.FINE, "Ignoring an unreadable message on the BungeeCord channel", e);
        }
    }

    private void complete(String key, String[] reply) {
        CompletableFuture<String[]> future;
        synchronized (pending) {
            Deque<CompletableFuture<String[]>> queue = pending.get(key);
            future = queue == null ? null : queue.poll();
        }
        if (future != null) {
            future.complete(reply);
        }
    }

    private static List<String> split(String csv) {
        return csv == null || csv.isBlank() ? List.of() : Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    /**
     * BungeeCord mode ({@code spigot.yml} settings.bungeecord) or Velocity
     * forwarding ({@code config/paper-global.yml} proxies.velocity.enabled).
     */
    private static boolean detectProxy() {
        File root = Bukkit.getWorldContainer();
        YamlConfiguration spigot = YamlConfiguration.loadConfiguration(new File(root, "spigot.yml"));
        YamlConfiguration paper = YamlConfiguration.loadConfiguration(new File(new File(root, "config"), "paper-global.yml"));
        return spigot.getBoolean("settings.bungeecord", false) || paper.getBoolean("proxies.velocity.enabled", false);
    }
}

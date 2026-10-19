package dev.universaladmin.network;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/**
 * UniversalAdmin across a proxy network (Velocity, BungeeCord). With
 * {@code network.enabled} every server that runs UniversalAdmin shares one
 * MySQL/MariaDB database and knows the others: which servers are up, who is
 * on which server, and a message channel between them. Requests to the
 * proxy itself (moving a player to another server, removing a player from
 * the whole network, listing servers that do not run UniversalAdmin) go
 * through the standard BungeeCord plugin messaging channel, which Velocity
 * and BungeeCord both understand - no proxy plugin required.
 *
 * <p>Always available through {@link dev.universaladmin.core.UniversalAdmin#network()}:
 * with network mode off, {@link #enabled()} is {@code false}, the server
 * lists contain only this server and {@link #publish} does nothing, so
 * callers never need a null check. See docs/user/network.md.
 */
public interface NetworkService {

    /** Whether network mode is active (enabled in the config and a shared MySQL/MariaDB database is used). */
    boolean enabled();

    /** This server's name as the proxy knows it (e.g. {@code lobby}). */
    String serverName();

    /**
     * Every known server: the ones running UniversalAdmin (with TPS, player
     * count and version) and - when the proxy could be asked - the ones that
     * don't. A snapshot, refreshed every few seconds; safe from any thread.
     */
    List<NetworkServer> servers();

    /** Every player online anywhere in the network (snapshot, see {@link #servers()}). */
    List<NetworkPlayer> players();

    /** Where a player is, if they are online anywhere in the network. */
    Optional<NetworkPlayer> player(UUID playerId);

    /** Like {@link #player(UUID)}, by name (case-insensitive). */
    Optional<NetworkPlayer> player(String name);

    /**
     * Sends a message to every other server running UniversalAdmin. Each
     * one passes it to the listeners {@link #subscribe subscribed} to
     * {@code topic}, on its main thread, usually within a second. The
     * sending server does not receive its own message. Topics should carry
     * the module's or extension's namespace ({@code "tickets.created"}).
     */
    void publish(String topic, Map<String, String> payload);

    /** Like {@link #publish(String, Map)}, but only for the server named {@code server}. */
    void publish(String topic, Map<String, String> payload, String server);

    /** Calls {@code listener} (on the main thread) for every message on {@code topic} from another server. */
    void subscribe(String topic, NetworkListener listener);

    void unsubscribe(String topic, NetworkListener listener);

    /**
     * Moves {@code player} (online on this server) to {@code server}
     * through the proxy. Completes with {@code false} if this server is not
     * behind a proxy or the server is unknown.
     */
    CompletableFuture<Boolean> connect(Player player, String server);

    /**
     * Moves a player who may be on any server to {@code server}, using
     * {@code carrier} (any player on this server) to reach the proxy.
     */
    CompletableFuture<Boolean> connectOther(Player carrier, String playerName, String server);

    /**
     * Removes {@code player} (online on this server) from the whole
     * network with {@code reason}. A plain kick from one server behind a
     * proxy would just move them to the fallback server - a ban must not.
     * Without a proxy this is an ordinary kick.
     */
    void disconnect(Player player, Component reason);

    /**
     * Moves {@code player} (online on this server) to {@code server} and,
     * once they arrived, to {@code target} there - a cross-server teleport.
     * On this server itself it is just a teleport. Completes with
     * {@code false} if the move was not possible (no proxy).
     */
    CompletableFuture<Boolean> sendTo(Player player, String server, ArrivalTarget target);

    /**
     * Brings {@code target} (on any server) to {@code destination} (online
     * on this server): through the proxy to this server, then next to them.
     */
    CompletableFuture<Boolean> bring(Player destination, NetworkPlayer target);

    /** Where a player moved with {@link #sendTo} ends up on the other server. */
    sealed interface ArrivalTarget {

        /** To the current position of another player on this server. */
        record ToPlayer(UUID playerId) implements ArrivalTarget {
        }

        /** To a position in a world of this server. */
        record ToLocation(String world, double x, double y, double z) implements ArrivalTarget {
        }
    }
}

package dev.universaladmin.network;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Storage of network mode - see {@link NetworkSchemaMigration}. Every server
 * writes only its own server row and its own players; everyone reads
 * everything. All methods run off the main thread.
 */
public interface NetworkRepository {

    /** One server's heartbeat. */
    record ServerRow(String name, int onlinePlayers, int maxPlayers, double tps, String version, Instant startedAt, Instant lastSeen) {
    }

    /** One player and the server they are on. */
    record PlayerRow(UUID playerId, String name, String server, Instant joinedAt) {
    }

    /** One message on the channel between the servers. */
    record MessageRow(long id, Instant createdAt, String source, String target, String topic, String payload) {
    }

    /** Writes this server's heartbeat and replaces its player list in one go. */
    CompletableFuture<Void> heartbeat(ServerRow server, Collection<PlayerRow> players);

    /** A player joined this server - visible to the others before the next heartbeat. */
    CompletableFuture<Void> playerJoined(PlayerRow player);

    /** A player left {@code server}; does nothing if they already show up on another server. */
    CompletableFuture<Void> playerLeft(UUID playerId, String server);

    /** Removes {@code server} and its players (shutdown, or a stale entry). */
    CompletableFuture<Void> removeServer(String server);

    CompletableFuture<List<ServerRow>> servers();

    CompletableFuture<List<PlayerRow>> players();

    /** Stores a message; {@code target == null} means "every server". */
    CompletableFuture<Void> publish(String source, String target, String topic, String payload, Instant createdAt);

    /** The id of the newest message, {@code 0} if there is none. */
    CompletableFuture<Long> latestMessageId();

    /** Messages for {@code server} (addressed to it or to everyone, not sent by it) with an id above {@code afterId}, oldest first. */
    CompletableFuture<List<MessageRow>> messagesSince(long afterId, String server, int limit);

    /** Deletes messages older than {@code before}. */
    CompletableFuture<Integer> pruneMessages(Instant before);
}

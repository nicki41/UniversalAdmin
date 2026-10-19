package dev.universaladmin.network;

import java.time.Instant;

/**
 * One server of the network.
 *
 * @param universalAdmin whether it runs UniversalAdmin in network mode - only
 *                       then are {@code tps}, {@code maxPlayers},
 *                       {@code version} and {@code lastSeen} known
 * @param online         whether it is up right now (for a server without
 *                       UniversalAdmin: whether the proxy listed it)
 * @param playerCount    players online on it
 * @param maxPlayers     its slot limit, {@code 0} if unknown
 * @param tps            ticks per second over the last minute, {@code 0} if unknown
 * @param version        the UniversalAdmin version it runs, {@code null} if unknown
 * @param lastSeen       its last heartbeat, {@code null} if unknown
 */
public record NetworkServer(
        String name, boolean universalAdmin, boolean online, int playerCount, int maxPlayers,
        double tps, String version, Instant lastSeen) {
}

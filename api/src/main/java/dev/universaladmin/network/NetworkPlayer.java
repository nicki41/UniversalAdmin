package dev.universaladmin.network;

import java.time.Instant;
import java.util.UUID;

/**
 * A player online somewhere in the network.
 *
 * @param id     their UUID - {@code null} for a player on a server without
 *               UniversalAdmin, whom only the proxy reported (by name)
 * @param server the server they are on
 * @param since  when they joined that server, {@code null} if unknown
 */
public record NetworkPlayer(UUID id, String name, String server, Instant since) {
}

package dev.universaladmin.core;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Everyone who ever joined a server using this database - in network mode
 * that is every server of the network, so a staff member on the lobby can
 * ban someone who only ever played on survival. Provided by the Players
 * module through the {@link ServiceRegistry}; commands use it through
 * {@link dev.universaladmin.command.CommandInvocation#withPlayer}.
 */
public interface PlayerDirectory {

    /** The player last seen with this name (case-insensitive). Runs off the main thread. */
    CompletableFuture<Optional<PlayerRef>> findByName(String name);

    /** The player with this UUID and the name they were last seen with. Runs off the main thread. */
    CompletableFuture<Optional<PlayerRef>> findById(UUID playerId);

    /**
     * Adds players this database has never seen - for example the players of
     * punishments imported from another plugin - so commands find them by
     * name. {@code lastSeen} is when the source last saw each player. Players
     * that are already known are left unchanged. Runs off the main thread and
     * completes with the number of players added; an implementation that
     * cannot add players completes with 0.
     *
     * @since 1.2
     */
    default CompletableFuture<Integer> addIfAbsent(Map<PlayerRef, Instant> lastSeen) {
        return CompletableFuture.completedFuture(0);
    }
}

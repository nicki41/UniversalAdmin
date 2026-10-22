package dev.universaladmin.modules.moderation.alts;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** {@code ua_player_addresses} and {@code ua_alt_key} - see {@link AltsMigration}. */
public interface AltRepository {

    /** The random hash key, created on first use; the same for every server sharing the database. */
    CompletableFuture<String> secret();

    /** Notes that {@code playerId} connected from the address with {@code addressHash} at {@code now}. */
    CompletableFuture<Void> record(UUID playerId, String playerName, String addressHash, Instant now);

    /**
     * Other accounts that used one of {@code playerId}'s addresses, both
     * sides seen since {@code since}; most recently seen first, at most
     * {@code limit}. {@link AltMatch#banned()} is always {@code false} here.
     */
    CompletableFuture<List<AltMatch>> findAlts(UUID playerId, Instant since, int limit);

    /** Deletes every address of {@code playerId}; how many rows went. */
    CompletableFuture<Integer> forget(UUID playerId);

    /** Deletes every address not seen since {@code cutoff}; how many rows went. */
    CompletableFuture<Integer> forgetBefore(Instant cutoff);
}

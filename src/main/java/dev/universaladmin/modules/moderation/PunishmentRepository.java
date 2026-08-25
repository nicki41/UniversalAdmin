package dev.universaladmin.modules.moderation;

import dev.universaladmin.storage.Repository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Punishment storage - see docs/architecture/storage.md for the general
 * repository contract every module follows.
 *
 * <p>"Is this currently in force" is never read off a pre-computed flag:
 * {@link #findActiveBan}/{@link #findActiveIpBan}/{@link #findActiveMute}
 * compute it at query time from {@code active} and {@code expires_at}
 * against the {@code now} passed in - the "TEMPBAN: no fragile
 * per-ban timer, determine status from expiresAt" requirement. The
 * {@code active} column itself only changes via an explicit revoke
 * ({@link #revokeById}/{@link #revokeActiveByTarget}) or the optional
 * periodic {@link #expireOverdue} sweep - both housekeeping, never
 * load-bearing for enforcement correctness.
 */
public interface PunishmentRepository extends Repository<Punishment, Long> {

    CompletableFuture<Optional<Punishment>> findActiveBan(UUID targetId, Instant now);

    CompletableFuture<Optional<Punishment>> findActiveIpBan(String ip, Instant now);

    CompletableFuture<Optional<Punishment>> findActiveMute(UUID targetId, Instant now);

    CompletableFuture<Optional<Punishment>> findActiveFreeze(UUID targetId, Instant now);

    /** Backs every GUI list (Active/Recent/Warnings/Bans/Mutes, one target's history) - see {@link PunishmentQuery}. */
    CompletableFuture<List<Punishment>> findByQuery(PunishmentQuery query);

    /**
     * Revokes every currently-active row of {@code types} for {@code targetId}
     * (UNBAN/UNMUTE) - each row is only flipped if it was still
     * {@code active = 1} at the moment of the update ({@code WHERE id = ? AND
     * active = 1}), so two concurrent revokes racing on the same row can only
     * ever have one of them report it as revoked.
     */
    CompletableFuture<List<Punishment>> revokeActiveByTarget(
            UUID targetId, Set<PunishmentType> types, Instant revokedAt, String revokedBy);

    /**
     * Revokes exactly one row by id (REMOVE_WARN, and the single-row path
     * {@link #revokeActiveByTarget} is built on) - empty if it was already
     * inactive or didn't exist. See the concurrency note above.
     */
    CompletableFuture<Optional<Punishment>> revokeById(long id, Instant revokedAt, String revokedBy);

    /** Bulk housekeeping sweep - flips {@code active} to {@code false} for every overdue row; returns the count affected. */
    CompletableFuture<Integer> expireOverdue(Instant now);
}

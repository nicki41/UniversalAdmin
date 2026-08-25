package dev.universaladmin.modules.moderation;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Application service for the Moderation module - the layer between
 * frontends (actions/GUI) and {@link PunishmentRepository}. "What does it
 * mean to issue/revoke a punishment" business logic lives here, not in the
 * {@code Action} classes that call it or the join/chat listeners that query it.
 */
public final class PunishmentService {

    private final PunishmentRepository repository;

    public PunishmentService(PunishmentRepository repository) {
        this.repository = repository;
    }

    /** Persists a new punishment. {@code expiresAt == null} means permanent. */
    public CompletableFuture<Punishment> issue(
            PunishmentType type, UUID targetId, String targetLastKnownName, String targetIp,
            UUID actorId, String actorName, String reason, Instant expiresAt) {
        return repository.save(Punishment.issue(type, targetId, targetLastKnownName, targetIp, actorId, actorName, reason, expiresAt));
    }

    /** Records a kick for history - see {@link Punishment#kick}. Kicking the live player is the action's own responsibility. */
    public CompletableFuture<Punishment> recordKick(
            UUID targetId, String targetLastKnownName, UUID actorId, String actorName, String reason) {
        return repository.save(Punishment.kick(targetId, targetLastKnownName, actorId, actorName, reason));
    }

    /** The currently-in-force ban (permanent or temp) for {@code targetId}, if any - the join-check query. */
    public CompletableFuture<Optional<Punishment>> activeBan(UUID targetId) {
        return repository.findActiveBan(targetId, Instant.now());
    }

    /** The currently-in-force IP ban for {@code ip}, if any - the other half of the join check. */
    public CompletableFuture<Optional<Punishment>> activeIpBan(String ip) {
        return repository.findActiveIpBan(ip, Instant.now());
    }

    /** The currently-in-force mute for {@code targetId}, if any - the chat-check query. */
    public CompletableFuture<Optional<Punishment>> activeMute(UUID targetId) {
        return repository.findActiveMute(targetId, Instant.now());
    }

    /** The currently-in-force freeze for {@code targetId}, if any - the freeze-enforcement listeners' query. */
    public CompletableFuture<Optional<Punishment>> activeFreeze(UUID targetId) {
        return repository.findActiveFreeze(targetId, Instant.now());
    }

    /** Revokes every currently-active BAN/TEMP_BAN/IP_BAN row for {@code targetId}. */
    public CompletableFuture<List<Punishment>> unban(UUID targetId, String revokedBy) {
        return repository.revokeActiveByTarget(
                targetId, Set.of(PunishmentType.BAN, PunishmentType.TEMP_BAN, PunishmentType.IP_BAN), Instant.now(), revokedBy);
    }

    /** Revokes every currently-active MUTE/TEMP_MUTE row for {@code targetId}. */
    public CompletableFuture<List<Punishment>> unmute(UUID targetId, String revokedBy) {
        return repository.revokeActiveByTarget(
                targetId, Set.of(PunishmentType.MUTE, PunishmentType.TEMP_MUTE), Instant.now(), revokedBy);
    }

    /** Revokes the currently-active FREEZE for {@code targetId}, if any. */
    public CompletableFuture<List<Punishment>> unfreeze(UUID targetId, String revokedBy) {
        return repository.revokeActiveByTarget(targetId, Set.of(PunishmentType.FREEZE), Instant.now(), revokedBy);
    }

    /** Revokes exactly one warning by id - empty if it was already removed or never existed. */
    public CompletableFuture<Optional<Punishment>> removeWarn(long warnId, String revokedBy) {
        return repository.revokeById(warnId, Instant.now(), revokedBy);
    }

    /** Backs every GUI list (Active/Recent/Warnings/Bans/Mutes, one target's history). */
    public CompletableFuture<List<Punishment>> history(PunishmentQuery query) {
        return repository.findByQuery(query);
    }

    public CompletableFuture<Optional<Punishment>> findById(long id) {
        return repository.findById(id);
    }

    /** Housekeeping sweep - flips {@code active} to {@code false} for every overdue row; returns the count affected. */
    public CompletableFuture<Integer> expireOverdue() {
        return repository.expireOverdue(Instant.now());
    }
}

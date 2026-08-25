package dev.universaladmin.modules.moderation;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * One row in the {@code punishments} table - every punishment type the
 * module supports (see {@link PunishmentType}), kicks and warns included, so
 * "Recent Punishments"/history has one source. {@code id == 0} for a
 * not-yet-persisted instance (see {@link PunishmentRepository#save}).
 *
 * <p>{@code targetIp} is populated only for {@link PunishmentType#IP_BAN}
 * rows. {@code actorId} is {@code null} for a console/system actor.
 * {@code expiresAt == null} means permanent. "Currently in force" is never
 * read off {@link #active()} alone - see {@link PunishmentRepository}'s
 * class javadoc for why expiry is always checked at query time instead.
 */
public record Punishment(
        long id,
        PunishmentType type,
        UUID targetId,
        String targetLastKnownName,
        String targetIp,
        UUID actorId,
        String actorName,
        String reason,
        Instant createdAt,
        Instant expiresAt,
        boolean active,
        Instant revokedAt,
        String revokedBy,
        Map<String, String> metadata) {

    public Punishment {
        metadata = Map.copyOf(metadata);
    }

    /** A fresh, not-yet-persisted, permanent-until-told-otherwise punishment. */
    public static Punishment issue(
            PunishmentType type, UUID targetId, String targetLastKnownName, String targetIp,
            UUID actorId, String actorName, String reason, Instant expiresAt) {
        return new Punishment(0, type, targetId, targetLastKnownName, targetIp, actorId, actorName, reason,
                Instant.now(), expiresAt, true, null, null, Map.of());
    }

    /**
     * A {@link PunishmentType#KICK} record - momentary, never "in force", so
     * unlike {@link #issue} it is persisted already inactive: it exists only
     * for "Recent Punishments" history, never for an active-punishment query.
     */
    public static Punishment kick(UUID targetId, String targetLastKnownName, UUID actorId, String actorName, String reason) {
        return new Punishment(0, PunishmentType.KICK, targetId, targetLastKnownName, null, actorId, actorName, reason,
                Instant.now(), null, false, null, null, Map.of());
    }

    public boolean permanent() {
        return expiresAt == null;
    }

    /** Whether this punishment is still in force at {@code now} - see {@link PunishmentRepository}. */
    public boolean isActiveAt(Instant now) {
        return active && (expiresAt == null || expiresAt.isAfter(now));
    }

    public Punishment revoke(Instant revokedAt, String revokedBy) {
        return new Punishment(id, type, targetId, targetLastKnownName, targetIp, actorId, actorName, reason,
                createdAt, expiresAt, false, revokedAt, revokedBy, metadata);
    }

    public Punishment withId(long id) {
        return new Punishment(id, type, targetId, targetLastKnownName, targetIp, actorId, actorName, reason,
                createdAt, expiresAt, active, revokedAt, revokedBy, metadata);
    }
}

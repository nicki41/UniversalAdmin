package dev.universaladmin.modules.moderation;

import dev.universaladmin.status.MuteKind;
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
 *
 * @param template the id of the punishment template it was issued with
 *                 ({@code /punish}), {@code null} for one issued directly - what
 *                 escalation counts, see
 *                 {@link dev.universaladmin.modules.moderation.templates.PunishmentTemplateService}
 * @param muteKind where a mute silences the player - meaningful for
 *                 {@code MUTE}/{@code TEMP_MUTE} only, {@link MuteKind#FULL}
 *                 for every other row and every mute issued before 1.5.0
 *                 (see ADR 0022)
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
        Map<String, String> metadata,
        String template,
        MuteKind muteKind) {

    public Punishment {
        metadata = Map.copyOf(metadata);
        muteKind = muteKind == null ? MuteKind.FULL : muteKind;
    }

    /** A punishment without a mute kind of its own - every type but a chat- or voice-only mute. */
    public Punishment(
            long id, PunishmentType type, UUID targetId, String targetLastKnownName, String targetIp,
            UUID actorId, String actorName, String reason, Instant createdAt, Instant expiresAt, boolean active,
            Instant revokedAt, String revokedBy, Map<String, String> metadata, String template) {
        this(id, type, targetId, targetLastKnownName, targetIp, actorId, actorName, reason, createdAt, expiresAt, active,
                revokedAt, revokedBy, metadata, template, MuteKind.FULL);
    }

    /** A fresh, not-yet-persisted, permanent-until-told-otherwise punishment. */
    public static Punishment issue(
            PunishmentType type, UUID targetId, String targetLastKnownName, String targetIp,
            UUID actorId, String actorName, String reason, Instant expiresAt) {
        return issue(type, targetId, targetLastKnownName, targetIp, actorId, actorName, reason, expiresAt, null);
    }

    /** Like {@link #issue(PunishmentType, UUID, String, String, UUID, String, String, Instant)}, issued with {@code template}. */
    public static Punishment issue(
            PunishmentType type, UUID targetId, String targetLastKnownName, String targetIp,
            UUID actorId, String actorName, String reason, Instant expiresAt, String template) {
        return issue(type, targetId, targetLastKnownName, targetIp, actorId, actorName, reason, expiresAt, template, MuteKind.FULL);
    }

    /** Like {@link #issue(PunishmentType, UUID, String, String, UUID, String, String, Instant, String)}, a mute of {@code muteKind}. */
    public static Punishment issue(
            PunishmentType type, UUID targetId, String targetLastKnownName, String targetIp,
            UUID actorId, String actorName, String reason, Instant expiresAt, String template, MuteKind muteKind) {
        return new Punishment(0, type, targetId, targetLastKnownName, targetIp, actorId, actorName, reason,
                Instant.now(), expiresAt, true, null, null, Map.of(), template, muteKind);
    }

    /**
     * A {@link PunishmentType#KICK} record - momentary, never "in force", so
     * unlike {@link #issue} it is persisted already inactive: it exists only
     * for "Recent Punishments" history, never for an active-punishment query.
     */
    public static Punishment kick(
            UUID targetId, String targetLastKnownName, UUID actorId, String actorName, String reason, String template) {
        return new Punishment(0, PunishmentType.KICK, targetId, targetLastKnownName, null, actorId, actorName, reason,
                Instant.now(), null, false, null, null, Map.of(), template);
    }

    public boolean permanent() {
        return expiresAt == null;
    }

    /** Whether this is a mute ({@code MUTE} or {@code TEMP_MUTE}). */
    public boolean isMute() {
        return type == PunishmentType.MUTE || type == PunishmentType.TEMP_MUTE;
    }

    /** Whether this punishment is still in force at {@code now} - see {@link PunishmentRepository}. */
    public boolean isActiveAt(Instant now) {
        return active && (expiresAt == null || expiresAt.isAfter(now));
    }

    public Punishment revoke(Instant revokedAt, String revokedBy) {
        return new Punishment(id, type, targetId, targetLastKnownName, targetIp, actorId, actorName, reason,
                createdAt, expiresAt, false, revokedAt, revokedBy, metadata, template, muteKind);
    }

    public Punishment withId(long id) {
        return new Punishment(id, type, targetId, targetLastKnownName, targetIp, actorId, actorName, reason,
                createdAt, expiresAt, active, revokedAt, revokedBy, metadata, template, muteKind);
    }
}

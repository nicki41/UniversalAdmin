package dev.universaladmin.modules.moderation.importing;

import dev.universaladmin.modules.moderation.Punishment;
import dev.universaladmin.modules.moderation.PunishmentType;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * One punishment read from another plugin, already translated into
 * UniversalAdmin's terms. {@code sourceId} identifies the entry within its
 * source ({@code ban:123}) - together with the source's id it is what makes
 * an import repeatable without duplicates.
 *
 * <p>The canonical constructor normalises what other plugins store
 * loosely: a ban or mute with an expiry becomes the TEMP_ variant (and
 * back), names are cut to the 16 characters the {@code punishments} table
 * holds, reasons to 1024, and a missing creation time becomes "now".
 *
 * @param targetIp only for {@link PunishmentType#IP_BAN}
 * @param server   the server the source recorded it on, if any (network setups)
 */
public record ImportedPunishment(
        String sourceId,
        PunishmentType type,
        UUID targetId,
        String targetName,
        String targetIp,
        UUID actorId,
        String actorName,
        String reason,
        Instant createdAt,
        Instant expiresAt,
        boolean active,
        Instant revokedAt,
        String revokedBy,
        String server) {

    static final int MAX_NAME = 16;
    static final int MAX_REASON = 1024;
    static final int MAX_ACTOR = 255;

    public ImportedPunishment {
        Objects.requireNonNull(sourceId, "sourceId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(targetId, "targetId");
        type = normalise(type, expiresAt);
        targetName = limit(blankToNull(targetName) == null ? ImportTargets.UNKNOWN_NAME : targetName.trim(), MAX_NAME);
        actorName = limit(blankToNull(actorName) == null ? "CONSOLE" : actorName.trim(), MAX_ACTOR);
        reason = blankToNull(reason) == null ? null : limit(reason.trim(), MAX_REASON);
        createdAt = createdAt == null ? Instant.now() : createdAt;
        revokedBy = blankToNull(revokedBy) == null ? null : limit(revokedBy.trim(), MAX_ACTOR);
        if (type == PunishmentType.KICK) {
            active = false;
        }
        server = blankToNull(server);
    }

    /** The row to store, tagged with where it came from (see the {@code imported_from}/{@code import_id} metadata). */
    public Punishment toPunishment(String source) {
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("imported_from", source);
        metadata.put("import_id", sourceId);
        if (server != null) {
            metadata.put("server", server);
        }
        return new Punishment(0, type, targetId, targetName, targetIp, actorId, actorName, reason,
                createdAt, expiresAt, active, revokedAt, revokedBy, metadata);
    }

    /** Whether this punishment is in force at {@code now} - for the preview's "active" counts. */
    public boolean activeAt(Instant now) {
        return active && (expiresAt == null || expiresAt.isAfter(now));
    }

    private static PunishmentType normalise(PunishmentType type, Instant expiresAt) {
        boolean temporary = expiresAt != null;
        return switch (type) {
            case BAN, TEMP_BAN -> temporary ? PunishmentType.TEMP_BAN : PunishmentType.BAN;
            case MUTE, TEMP_MUTE -> temporary ? PunishmentType.TEMP_MUTE : PunishmentType.MUTE;
            default -> type;
        };
    }

    static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    static String limit(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}

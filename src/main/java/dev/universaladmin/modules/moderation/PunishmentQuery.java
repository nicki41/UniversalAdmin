package dev.universaladmin.modules.moderation;

import java.util.Set;
import java.util.UUID;

/**
 * Filter/limit for {@link PunishmentRepository#findByQuery} - backs every
 * GUI list (Active/Recent/Warnings/Bans/Mutes, and a single target's
 * history), always newest-first. {@code types == null} means every type;
 * {@code active == null} means don't filter by active/expired at all,
 * {@code true} means "currently in force" (see {@link Punishment#isActiveAt}),
 * {@code false} means "no longer in force".
 */
public record PunishmentQuery(UUID targetId, Set<PunishmentType> types, Boolean active, int limit) {

    public PunishmentQuery {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive, was " + limit);
        }
        types = types == null ? null : Set.copyOf(types);
    }

    public static PunishmentQuery recent(int limit) {
        return new PunishmentQuery(null, null, null, limit);
    }

    public static PunishmentQuery active(int limit) {
        return new PunishmentQuery(null, null, true, limit);
    }

    public static PunishmentQuery ofTypes(Set<PunishmentType> types, int limit) {
        return new PunishmentQuery(null, types, null, limit);
    }

    public static PunishmentQuery forTarget(UUID targetId, int limit) {
        return new PunishmentQuery(targetId, null, null, limit);
    }
}

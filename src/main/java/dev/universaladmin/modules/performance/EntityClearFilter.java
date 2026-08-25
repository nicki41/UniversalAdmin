package dev.universaladmin.modules.performance;

import java.util.Locale;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;

/**
 * The one place "is this entity safe to remove" is decided - shared by
 * {@link PerformanceSamplingService#previewClearCount} (preview count) and
 * {@link dev.universaladmin.modules.performance.action.ClearEntitiesAction}
 * (the actual removal), so a preview shown to a player can never drift from
 * what the action then does.
 *
 * <p>See docs/development/architecture-rules.md's "Entity Clear" requirements: never all entities
 * indiscriminately, never players, dangerous/valuable types excluded by
 * default. Beyond the configured {@code protected-types} list
 * ({@link PerformanceSettings#ENTITY_CLEAR_PROTECTED_TYPES}), this also
 * always protects named, tamed, and leashed entities - a config typo should
 * not be the only thing standing between a lag-cleanup click and someone's
 * named pet.
 */
public final class EntityClearFilter {

    private EntityClearFilter() {
    }

    public static boolean isClearable(Entity entity, Set<EntityType> targetTypes) {
        if (entity instanceof Player) {
            return false;
        }
        if (!targetTypes.contains(entity.getType())) {
            return false;
        }
        if (entity.customName() != null) {
            return false;
        }
        if (entity instanceof Tameable tameable && tameable.isTamed()) {
            return false;
        }
        return !(entity instanceof LivingEntity living && living.isLeashed());
    }

    /** Resolves {@link PerformanceSettings#ENTITY_CLEAR_PROTECTED_TYPES}, skipping and logging any name that is no longer a valid {@link EntityType}. */
    public static Set<EntityType> resolveProtectedTypes(java.util.List<String> configuredNames, Logger logger) {
        Set<EntityType> protectedTypes = java.util.EnumSet.noneOf(EntityType.class);
        for (String name : configuredNames) {
            try {
                protectedTypes.add(EntityType.valueOf(name.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                logger.log(Level.WARNING, () -> "performance.entity-clear.protected-types: '" + name + "' is not a known entity type, ignoring it");
            }
        }
        return protectedTypes;
    }

    /** {@code requested} with every {@link PerformanceSettings#ENTITY_CLEAR_PROTECTED_TYPES} entry removed - the effective, safety-net-applied target set. */
    public static Set<EntityType> effectiveTargets(Set<EntityType> requested, Set<EntityType> protectedTypes) {
        Set<EntityType> effective = java.util.EnumSet.noneOf(EntityType.class);
        effective.addAll(requested);
        effective.removeAll(protectedTypes);
        return effective;
    }
}

package dev.universaladmin.modules.performance.action;

import java.util.Set;
import org.bukkit.entity.EntityType;

/**
 * Input for {@link ClearEntitiesAction}.
 *
 * @param entityTypes the types to remove, before the action's own protected-types safety net is applied - never empty (validated)
 * @param worldName   a single world to limit the clear to, or {@code null} for every currently loaded world
 */
public record ClearEntitiesInput(Set<EntityType> entityTypes, String worldName) {
}

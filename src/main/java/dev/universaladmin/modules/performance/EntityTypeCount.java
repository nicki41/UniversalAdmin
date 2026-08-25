package dev.universaladmin.modules.performance;

import org.bukkit.entity.EntityType;

/** One row of the "Entity Overview - by type" list, sorted descending by {@link #count()}. */
public record EntityTypeCount(EntityType type, int count) {
}

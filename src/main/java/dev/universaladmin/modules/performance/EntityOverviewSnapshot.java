package dev.universaladmin.modules.performance;

import java.time.Instant;
import java.util.List;

/**
 * Entities grouped by type (across every loaded world) and by world - see
 * {@link PerformanceSamplingService#entityOverview()}. Players are never
 * counted here (they already have their own "Online Players" dashboard
 * tile) - see {@link PerformanceSamplingService}'s javadoc.
 */
public record EntityOverviewSnapshot(List<EntityTypeCount> byType, List<WorldPerformanceSnapshot> byWorld, int totalEntities, Instant sampledAt) {

    public static final EntityOverviewSnapshot EMPTY = new EntityOverviewSnapshot(List.of(), List.of(), 0, Instant.EPOCH);
}

package dev.universaladmin.modules.performance;

/** One row of the "World Performance" view - see {@link PerformanceSamplingService#worldSnapshots()}. */
public record WorldPerformanceSnapshot(String worldName, int players, int loadedChunks, int entities) {
}

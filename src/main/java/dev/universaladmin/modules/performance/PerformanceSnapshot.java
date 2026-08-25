package dev.universaladmin.modules.performance;

import java.time.Duration;
import java.time.Instant;

/**
 * One point-in-time read of everything the Performance dashboard shows -
 * built by {@link PerformanceSamplingService} on its own refresh interval,
 * never recomputed on GUI render (see docs/development/architecture-rules.md's "Performance Sampling"
 * requirement: caching, no expensive work per render).
 *
 * @param tps1m             {@code Server#getTPS()[0]} - 1 minute average
 * @param tps5m             {@code Server#getTPS()[1]} - 5 minute average
 * @param tps15m            {@code Server#getTPS()[2]} - 15 minute average
 * @param mspt              {@code Server#getAverageTickTime()} - average milliseconds per tick
 * @param usedMemoryBytes   {@code Runtime#totalMemory() - Runtime#freeMemory()}
 * @param maxMemoryBytes    {@code Runtime#maxMemory()}
 * @param onlinePlayers     {@code Bukkit#getOnlinePlayers().size()}
 * @param loadedChunks      sum of {@code World#getChunkCount()} across every loaded world
 * @param entityCount       sum of non-player entities across every loaded world - see {@link PerformanceSamplingService}
 * @param worldCount        {@code Bukkit#getWorlds().size()}
 * @param uptime            time since the plugin enabled
 * @param sampledAt         when this snapshot was taken
 */
public record PerformanceSnapshot(
        double tps1m,
        double tps5m,
        double tps15m,
        double mspt,
        long usedMemoryBytes,
        long maxMemoryBytes,
        int onlinePlayers,
        int loadedChunks,
        int entityCount,
        int worldCount,
        Duration uptime,
        Instant sampledAt) {

    public double usedMemoryPercent() {
        return maxMemoryBytes <= 0 ? 0.0 : (usedMemoryBytes * 100.0) / maxMemoryBytes;
    }
}

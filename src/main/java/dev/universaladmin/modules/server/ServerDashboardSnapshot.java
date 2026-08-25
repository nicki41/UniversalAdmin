package dev.universaladmin.modules.server;

import dev.universaladmin.core.ComponentStatus;
import java.time.Duration;
import java.util.List;
import java.util.OptionalDouble;

/**
 * A point-in-time read of everything the "Server Dashboard" shows - never
 * cached, built fresh on every {@link ServerDashboardService#snapshot()}
 * call, the same "always current" philosophy as {@link
 * dev.universaladmin.core.PluginStatus}.
 *
 * @param cpuLoadPercent process CPU load in {@code [0.0, 100.0]}, empty if the JVM doesn't expose it reliably (see "soweit zuverlässig")
 */
public record ServerDashboardSnapshot(
        String pluginVersion,
        String paperVersion,
        String minecraftVersion,
        String javaVersion,
        Duration uptime,
        int onlinePlayers,
        int maxPlayers,
        int worldCount,
        long usedMemoryBytes,
        long maxMemoryBytes,
        int availableProcessors,
        OptionalDouble cpuLoadPercent,
        ComponentStatus databaseStatus,
        List<String> enabledModules) {
}

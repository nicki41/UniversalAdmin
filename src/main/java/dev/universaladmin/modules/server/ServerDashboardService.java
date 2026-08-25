package dev.universaladmin.modules.server;

import dev.universaladmin.core.PluginStatus;
import io.papermc.paper.ServerBuildInfo;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.util.List;
import java.util.OptionalDouble;
import java.util.function.Supplier;
import org.bukkit.Bukkit;

/**
 * Pure read of the server's current health/environment - no persistence, no
 * {@link dev.universaladmin.action.Action} (nothing here is a mutation worth
 * auditing), just a fresh {@link ServerDashboardSnapshot} on every call. Only
 * depends on {@link PluginStatus} (already has version/uptime/modules/db
 * status) rather than the whole {@code UniversalAdmin} platform, plus a
 * handful of direct Bukkit/JVM reads - must be called from the main thread
 * (it reads {@link Bukkit#getOnlinePlayers()}/{@link Bukkit#getWorlds()}),
 * same as any other GUI render.
 */
public final class ServerDashboardService {

    private final Supplier<PluginStatus> statusSupplier;

    public ServerDashboardService(Supplier<PluginStatus> statusSupplier) {
        this.statusSupplier = statusSupplier;
    }

    public ServerDashboardSnapshot snapshot() {
        PluginStatus status = statusSupplier.get();
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();

        List<String> enabledModules = status.activeModules().stream().map(id -> id.key().name()).toList();

        return new ServerDashboardSnapshot(
                status.version(),
                paperVersion(),
                minecraftVersion(),
                System.getProperty("java.version"),
                status.uptime(),
                Bukkit.getOnlinePlayers().size(),
                Bukkit.getMaxPlayers(),
                Bukkit.getWorlds().size(),
                usedMemory,
                maxMemory,
                runtime.availableProcessors(),
                processCpuLoadPercent(),
                status.database(),
                enabledModules);
    }

    private String paperVersion() {
        try {
            ServerBuildInfo buildInfo = ServerBuildInfo.buildInfo();
            String build = buildInfo.buildNumber().isPresent() ? " (build " + buildInfo.buildNumber().getAsInt() + ")" : "";
            return buildInfo.brandName() + " " + buildInfo.minecraftVersionId() + build;
        } catch (RuntimeException | LinkageError e) {
            return Bukkit.getVersion();
        }
    }

    private String minecraftVersion() {
        try {
            return ServerBuildInfo.buildInfo().minecraftVersionId();
        } catch (RuntimeException | LinkageError e) {
            return Bukkit.getBukkitVersion();
        }
    }

    /** "Soweit zuverlässig": {@code com.sun.management}'s process CPU load is HotSpot-specific and not guaranteed on every JVM. */
    private OptionalDouble processCpuLoadPercent() {
        OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
        if (osBean instanceof com.sun.management.OperatingSystemMXBean sunBean) {
            double load = sunBean.getProcessCpuLoad();
            return load >= 0 ? OptionalDouble.of(load * 100.0) : OptionalDouble.empty();
        }
        return OptionalDouble.empty();
    }
}

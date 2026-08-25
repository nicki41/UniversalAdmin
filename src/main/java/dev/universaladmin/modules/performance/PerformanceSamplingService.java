package dev.universaladmin.modules.performance;

import dev.universaladmin.core.PluginStatus;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.localization.MessageService;
import dev.universaladmin.notification.Notification;
import dev.universaladmin.notification.NotificationService;
import dev.universaladmin.settings.SettingsService;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

/**
 * Samples TPS/MSPT/memory/world/entity state on a fixed interval
 * ({@code core.performance.refresh-interval}) and caches the result -
 * {@link PerformanceHomePage}/{@link PerformanceWorldsPage}/{@link
 * PerformanceEntityOverviewPage} only ever read the cache, never recompute
 * on render (docs/development/architecture-rules.md's "Performance Sampling": sensible refresh intervals,
 * caching, no expensive per-render work).
 *
 * <p>{@link #sample()} must always run on the main thread: it reads
 * {@link World#getEntities()}/{@link World#getChunkCount()}, exactly like
 * {@code WorldInfoService}/{@code ServerDashboardService} - see
 * docs/architecture/threading.md. {@link PerformanceModule} schedules it via
 * a Bukkit repeating task, never {@code TaskScheduler.runAsync}.
 *
 * <p>Players are never counted as "entities" here - they already have their
 * own "Online Players" tile/permission model, and counting them would make
 * the Entity Overview (and Entity Clear, which reuses the same type set)
 * misleading about what's actually contributing to entity-related lag.
 */
public final class PerformanceSamplingService {

    private final Supplier<PluginStatus> statusSupplier;
    private final NotificationService notifications;
    private final MessageService messages;
    private final SettingsService settings;
    private final Logger logger;
    private final PerformanceHistory history = new PerformanceHistory();

    private final AtomicReference<PerformanceSnapshot> snapshot = new AtomicReference<>();
    private final AtomicReference<List<WorldPerformanceSnapshot>> worldSnapshots = new AtomicReference<>(List.of());
    private final AtomicReference<EntityOverviewSnapshot> entityOverview = new AtomicReference<>(EntityOverviewSnapshot.EMPTY);

    private final AlertState tpsAlert = new AlertState();
    private final AlertState msptAlert = new AlertState();
    private final AlertState memoryAlert = new AlertState();

    public PerformanceSamplingService(
            Supplier<PluginStatus> statusSupplier, NotificationService notifications, MessageService messages,
            SettingsService settings, Logger logger) {
        this.statusSupplier = statusSupplier;
        this.notifications = notifications;
        this.messages = messages;
        this.settings = settings;
        this.logger = logger;
    }

    /** Never {@code null} after the first {@link #sample()} - {@link PerformanceModule} samples once synchronously during {@code onEnable}. */
    public PerformanceSnapshot snapshot() {
        return snapshot.get();
    }

    public List<WorldPerformanceSnapshot> worldSnapshots() {
        return worldSnapshots.get();
    }

    public EntityOverviewSnapshot entityOverview() {
        return entityOverview.get();
    }

    public PerformanceHistory history() {
        return history;
    }

    /** Whether {@link PerformanceSettings#ENTITY_CLEAR_PROTECTED_TYPES} currently protects {@code type} - checked by the GUI before offering a per-type clear. */
    public boolean isProtected(EntityType type) {
        return EntityClearFilter.resolveProtectedTypes(settings.get(PerformanceSettings.ENTITY_CLEAR_PROTECTED_TYPES), logger).contains(type);
    }

    /** Live count of entities {@link EntityClearFilter} would actually remove for this selection - always current, computed on demand (main thread only), not cached. */
    public int previewClearCount(Set<EntityType> requestedTypes, String worldName) {
        Set<EntityType> protectedTypes = EntityClearFilter.resolveProtectedTypes(
                settings.get(PerformanceSettings.ENTITY_CLEAR_PROTECTED_TYPES), logger);
        Set<EntityType> effectiveTargets = EntityClearFilter.effectiveTargets(requestedTypes, protectedTypes);
        if (effectiveTargets.isEmpty()) {
            return 0;
        }
        List<World> worlds = worldName == null
                ? Bukkit.getWorlds()
                : Bukkit.getWorld(worldName) != null ? List.of(Bukkit.getWorld(worldName)) : List.of();
        int count = 0;
        for (World world : worlds) {
            for (Entity entity : world.getEntities()) {
                if (EntityClearFilter.isClearable(entity, effectiveTargets)) {
                    count++;
                }
            }
        }
        return count;
    }

    /** Takes a fresh sample, updates every cache above, records history, and checks alert thresholds. Main thread only. */
    public void sample() {
        PluginStatus status = statusSupplier.get();
        double[] tps = Bukkit.getServer().getTPS();
        double mspt = Bukkit.getServer().getAverageTickTime();
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        List<World> worlds = Bukkit.getWorlds();

        List<WorldPerformanceSnapshot> worldRows = new ArrayList<>(worlds.size());
        Map<EntityType, Integer> countsByType = new EnumMap<>(EntityType.class);
        int totalChunks = 0;
        int totalEntities = 0;
        for (World world : worlds) {
            int chunks = world.getChunkCount();
            int entities = 0;
            for (Entity entity : world.getEntities()) {
                if (entity instanceof Player) {
                    continue;
                }
                entities++;
                countsByType.merge(entity.getType(), 1, Integer::sum);
            }
            worldRows.add(new WorldPerformanceSnapshot(world.getName(), world.getPlayers().size(), chunks, entities));
            totalChunks += chunks;
            totalEntities += entities;
        }
        List<WorldPerformanceSnapshot> immutableWorldRows = List.copyOf(worldRows);

        Instant now = Instant.now();
        PerformanceSnapshot newSnapshot = new PerformanceSnapshot(
                tps[0], tps[1], tps[2], mspt, usedMemory, maxMemory, Bukkit.getOnlinePlayers().size(),
                totalChunks, totalEntities, worlds.size(), status.uptime(), now);

        List<EntityTypeCount> byType = countsByType.entrySet().stream()
                .map(entry -> new EntityTypeCount(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingInt(EntityTypeCount::count).reversed())
                .toList();

        snapshot.set(newSnapshot);
        worldSnapshots.set(immutableWorldRows);
        entityOverview.set(new EntityOverviewSnapshot(byType, immutableWorldRows, totalEntities, now));
        history.record(new PerformanceSample(now, tps[0], mspt, usedMemory));

        checkAlerts(newSnapshot);
    }

    private void checkAlerts(PerformanceSnapshot snap) {
        Duration cooldown = settings.get(PerformanceSettings.ALERT_COOLDOWN);
        double tpsThreshold = settings.get(PerformanceSettings.ALERT_TPS_THRESHOLD);
        double msptThreshold = settings.get(PerformanceSettings.ALERT_MSPT_THRESHOLD_MS);
        double memoryThreshold = settings.get(PerformanceSettings.ALERT_MEMORY_THRESHOLD_PERCENT);

        checkAlert(tpsAlert, snap.tps1m() < tpsThreshold, snap.sampledAt(), cooldown,
                "performance.alert.tps-low", tpsThreshold, snap.tps1m());
        checkAlert(msptAlert, snap.mspt() > msptThreshold, snap.sampledAt(), cooldown,
                "performance.alert.mspt-high", msptThreshold, snap.mspt());
        checkAlert(memoryAlert, snap.usedMemoryPercent() > memoryThreshold, snap.sampledAt(), cooldown,
                "performance.alert.memory-high", memoryThreshold, snap.usedMemoryPercent());
    }

    /**
     * Fires at most once per {@code cooldown} while a threshold stays breached
     * (not once per refresh interval) - "noch keine komplexe Alert Engine",
     * just enough hysteresis that a persisting breach doesn't spam staff.
     * Delivered through {@link NotificationService#notifyStaff}, the same
     * interface a future Discord/web-push channel implements - see its
     * javadoc - rather than a bespoke alert transport for this module.
     */
    private void checkAlert(AlertState state, boolean breached, Instant now, Duration cooldown, String messageKey, double threshold, double current) {
        if (!breached) {
            state.active = false;
            return;
        }
        if (state.active && Duration.between(state.lastFired, now).compareTo(cooldown) < 0) {
            return;
        }
        state.active = true;
        state.lastFired = now;
        String text = messages.get(MessageKey.of(messageKey), format(threshold), format(current));
        notifications.notifyStaff(PerformancePermissions.VIEW, Notification.info(text));
    }

    private String format(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static final class AlertState {
        private boolean active;
        private Instant lastFired = Instant.EPOCH;
    }
}

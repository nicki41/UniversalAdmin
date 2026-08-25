package dev.universaladmin.modules.worlds;

import org.bukkit.World;
import org.bukkit.WorldBorder;

/**
 * Pure reads of live {@link World} state - no persistence, no {@link
 * dev.universaladmin.action.Action} (nothing here is a mutation worth
 * auditing), mirrors {@code ServerDashboardService}. Must be called from the
 * main thread, like any other direct Bukkit read.
 */
public final class WorldInfoService {

    private static final int TICKS_PER_SECOND = 20;

    public WorldSummary summary(World world) {
        return new WorldSummary(
                world.getName(), world.getEnvironment(), world.getPlayers().size(), world.getChunkCount(),
                world.getEntityCount(), world.getDifficulty(), world.getTime(), world.hasStorm(), world.isThundering());
    }

    /** @param includeSeed whether the caller holds {@link WorldsPermissions#VIEW_SEED} - the seed is omitted entirely otherwise. */
    public WorldProfileSnapshot profile(World world, boolean includeSeed) {
        return new WorldProfileSnapshot(
                world.getName(), world.getEnvironment(), includeSeed ? world.getSeed() : null, world.getSpawnLocation(),
                border(world), world.getPlayers().size(), world.getChunkCount(), world.getEntityCount(),
                world.getDifficulty(), world.getTime(), world.hasStorm(), world.isThundering());
    }

    /** {@link WorldBorderSnapshot#warningTime()} is in seconds - converted from {@link WorldBorder#getWarningTimeTicks()} (the ticks-based getter is current; the seconds one is deprecated for removal). */
    public WorldBorderSnapshot border(World world) {
        WorldBorder border = world.getWorldBorder();
        return new WorldBorderSnapshot(
                border.getCenter(), border.getSize(), border.getDamageAmount(), border.getDamageBuffer(),
                border.getWarningDistance(), border.getWarningTimeTicks() / TICKS_PER_SECOND);
    }
}

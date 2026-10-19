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

    /** {@link WorldBorderSnapshot#warningTime()} is in seconds - see {@link WorldBorderWarnings} for why it is read through a helper. */
    public WorldBorderSnapshot border(World world) {
        WorldBorder border = world.getWorldBorder();
        return new WorldBorderSnapshot(
                border.getCenter(), border.getSize(), border.getDamageAmount(), border.getDamageBuffer(),
                border.getWarningDistance(), WorldBorderWarnings.seconds(border));
    }
}

package dev.universaladmin.modules.worlds;

import org.bukkit.Difficulty;
import org.bukkit.World;

/** One row of the World Browser - see {@link WorldInfoService#summary(World)}. */
public record WorldSummary(
        String name,
        World.Environment environment,
        int players,
        int loadedChunks,
        int entities,
        Difficulty difficulty,
        long time,
        boolean storm,
        boolean thundering) {
}

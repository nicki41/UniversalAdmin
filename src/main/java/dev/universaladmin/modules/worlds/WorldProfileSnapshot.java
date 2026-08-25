package dev.universaladmin.modules.worlds;

import org.bukkit.Difficulty;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * Everything the World Profile page shows - see {@link WorldInfoService#profile(World, boolean)}.
 *
 * @param seed {@code null} when the caller isn't permitted to see it - never attached to the
 *             snapshot at all in that case, the same pattern {@code GetPlayerIpAddressAction} uses for IP addresses
 */
public record WorldProfileSnapshot(
        String name,
        World.Environment environment,
        Long seed,
        Location spawn,
        WorldBorderSnapshot border,
        int players,
        int loadedChunks,
        int entities,
        Difficulty difficulty,
        long time,
        boolean storm,
        boolean thundering) {
}

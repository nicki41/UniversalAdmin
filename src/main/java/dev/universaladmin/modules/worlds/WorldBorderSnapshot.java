package dev.universaladmin.modules.worlds;

import org.bukkit.Location;

/** See {@link WorldInfoService#border(org.bukkit.World)}. */
public record WorldBorderSnapshot(
        Location center, double size, double damageAmount, double damageBuffer, int warningDistance, int warningTime) {
}

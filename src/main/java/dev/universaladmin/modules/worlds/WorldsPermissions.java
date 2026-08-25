package dev.universaladmin.modules.worlds;

import dev.universaladmin.permission.PermissionDefault;
import dev.universaladmin.permission.PermissionDefinition;
import dev.universaladmin.permission.PermissionNode;
import dev.universaladmin.permission.PermissionRegistry;
import java.util.List;

/**
 * Every {@link PermissionNode} the Worlds module declares, in one place -
 * see docs/user/permissions.md for the documented table these mirror.
 */
public final class WorldsPermissions {

    private WorldsPermissions() {
    }

    public static final PermissionNode VIEW = PermissionNode.core("worlds.view");
    /** Separate from {@link #VIEW} on purpose - a world's seed is more sensitive than the rest of its profile. */
    public static final PermissionNode VIEW_SEED = PermissionNode.core("worlds.view.seed");
    public static final PermissionNode TELEPORT = PermissionNode.core("worlds.teleport");
    public static final PermissionNode SPAWN = PermissionNode.core("worlds.spawn.set");
    public static final PermissionNode TIME = PermissionNode.core("worlds.time.set");
    public static final PermissionNode WEATHER = PermissionNode.core("worlds.weather.set");
    public static final PermissionNode DIFFICULTY = PermissionNode.core("worlds.difficulty.set");
    public static final PermissionNode BORDER = PermissionNode.core("worlds.border.manage");
    public static final PermissionNode GAMERULE = PermissionNode.core("worlds.gamerule.manage");

    public static void registerAll(PermissionRegistry registry) {
        List.of(
                new PermissionDefinition(VIEW, "Open the world browser and profile pages", PermissionDefault.OP),
                new PermissionDefinition(VIEW_SEED, "View a world's seed", PermissionDefault.OP),
                new PermissionDefinition(TELEPORT, "Teleport to a world's spawn", PermissionDefault.OP),
                new PermissionDefinition(SPAWN, "Set a world's spawn point", PermissionDefault.OP),
                new PermissionDefinition(TIME, "Set a world's time", PermissionDefault.OP),
                new PermissionDefinition(WEATHER, "Set a world's weather", PermissionDefault.OP),
                new PermissionDefinition(DIFFICULTY, "Set a world's difficulty", PermissionDefault.OP),
                new PermissionDefinition(BORDER, "Manage a world's border (center/size/damage/warning)", PermissionDefault.OP),
                new PermissionDefinition(GAMERULE, "Change a world's gamerules", PermissionDefault.OP))
                .forEach(registry::register);
    }
}

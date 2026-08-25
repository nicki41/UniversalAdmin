package dev.universaladmin.modules.players;

import dev.universaladmin.permission.PermissionDefault;
import dev.universaladmin.permission.PermissionDefinition;
import dev.universaladmin.permission.PermissionNode;
import dev.universaladmin.permission.PermissionRegistry;
import java.util.List;

/**
 * Every {@link PermissionNode} the Players module declares, in one place -
 * both the actions that require them and the GUI buttons that hide when
 * absent reference these constants instead of re-typing the raw string (see
 * docs/user/permissions.md for the documented table these mirror).
 */
public final class PlayerPermissions {

    private PlayerPermissions() {
    }

    public static final PermissionNode VIEW = PermissionNode.core("players.view");
    public static final PermissionNode IP = PermissionNode.core("players.ip");
    public static final PermissionNode TELEPORT = PermissionNode.core("players.teleport");
    public static final PermissionNode HEAL = PermissionNode.core("players.heal");
    public static final PermissionNode FEED = PermissionNode.core("players.feed");
    public static final PermissionNode EXTINGUISH = PermissionNode.core("players.extinguish");
    public static final PermissionNode EFFECTS_CLEAR = PermissionNode.core("players.effects.clear");
    public static final PermissionNode EFFECTS_ADD = PermissionNode.core("players.effects.add");
    public static final PermissionNode EFFECTS_REMOVE = PermissionNode.core("players.effects.remove");
    public static final PermissionNode SET_HEALTH = PermissionNode.core("players.set.health");
    public static final PermissionNode SET_FOOD = PermissionNode.core("players.set.food");
    public static final PermissionNode SET_XP = PermissionNode.core("players.set.xp");
    public static final PermissionNode SET_LEVEL = PermissionNode.core("players.set.level");
    public static final PermissionNode FLY_TOGGLE = PermissionNode.core("players.fly.toggle");
    public static final PermissionNode FLY_SPEED = PermissionNode.core("players.fly.speed");
    public static final PermissionNode WALK_SPEED = PermissionNode.core("players.walk-speed");
    public static final PermissionNode GLOW = PermissionNode.core("players.glow");
    public static final PermissionNode GRAVITY = PermissionNode.core("players.gravity");
    public static final PermissionNode COLLISION = PermissionNode.core("players.collision");
    public static final PermissionNode GAMEMODE = PermissionNode.core("players.gamemode");
    public static final PermissionNode INVENTORY_VIEW = PermissionNode.core("players.inventory.view");
    public static final PermissionNode INVENTORY_EDIT = PermissionNode.core("players.inventory.edit");
    public static final PermissionNode ENDERCHEST_VIEW = PermissionNode.core("players.enderchest.view");
    public static final PermissionNode ENDERCHEST_EDIT = PermissionNode.core("players.enderchest.edit");

    /** Registers every node above with {@link PermissionDefault#OP}, matching every other node in the plugin today. */
    public static void registerAll(PermissionRegistry registry) {
        List.of(
                new PermissionDefinition(VIEW, "View player profiles", PermissionDefault.OP),
                new PermissionDefinition(IP, "View a player's IP address", PermissionDefault.OP),
                new PermissionDefinition(TELEPORT, "Teleport players (admin<->player, world spawn, bed, coordinates)", PermissionDefault.OP),
                new PermissionDefinition(HEAL, "Heal a player", PermissionDefault.OP),
                new PermissionDefinition(FEED, "Feed a player", PermissionDefault.OP),
                new PermissionDefinition(EXTINGUISH, "Extinguish a player", PermissionDefault.OP),
                new PermissionDefinition(EFFECTS_CLEAR, "Clear a player's potion effects", PermissionDefault.OP),
                new PermissionDefinition(EFFECTS_ADD, "Add a potion effect to a player", PermissionDefault.OP),
                new PermissionDefinition(EFFECTS_REMOVE, "Remove one potion effect from a player", PermissionDefault.OP),
                new PermissionDefinition(SET_HEALTH, "Set a player's health", PermissionDefault.OP),
                new PermissionDefinition(SET_FOOD, "Set a player's food level", PermissionDefault.OP),
                new PermissionDefinition(SET_XP, "Set a player's experience progress", PermissionDefault.OP),
                new PermissionDefinition(SET_LEVEL, "Set a player's experience level", PermissionDefault.OP),
                new PermissionDefinition(FLY_TOGGLE, "Toggle whether a player may fly", PermissionDefault.OP),
                new PermissionDefinition(FLY_SPEED, "Set a player's fly speed", PermissionDefault.OP),
                new PermissionDefinition(WALK_SPEED, "Set a player's walk speed", PermissionDefault.OP),
                new PermissionDefinition(GLOW, "Toggle a player's glowing effect", PermissionDefault.OP),
                new PermissionDefinition(GRAVITY, "Toggle whether gravity affects a player", PermissionDefault.OP),
                new PermissionDefinition(COLLISION, "Toggle whether a player collides with entities", PermissionDefault.OP),
                new PermissionDefinition(GAMEMODE, "Change a player's gamemode", PermissionDefault.OP),
                new PermissionDefinition(INVENTORY_VIEW, "View a player's main inventory, armor, and offhand", PermissionDefault.OP),
                new PermissionDefinition(INVENTORY_EDIT, "Edit or clear a player's main inventory, armor, and offhand", PermissionDefault.OP),
                new PermissionDefinition(ENDERCHEST_VIEW, "View a player's ender chest", PermissionDefault.OP),
                new PermissionDefinition(ENDERCHEST_EDIT, "Edit a player's ender chest", PermissionDefault.OP))
                .forEach(registry::register);
    }
}

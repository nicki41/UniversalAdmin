package dev.universaladmin.modules.players.action;

import dev.universaladmin.action.ActionId;

/**
 * Every {@link ActionId} the Players module registers, in one place -
 * {@link PlayerActionRegistrar} builds each {@link dev.universaladmin.action.ActionDefinition}
 * under one of these, and the GUI package calls {@link
 * dev.universaladmin.action.ActionExecutor#execute} against the same
 * constant instead of retyping the raw string.
 */
public final class PlayerActionIds {

    private PlayerActionIds() {
    }

    public static final ActionId GET_PROFILE = ActionId.core("players.get-profile");
    public static final ActionId GET_IP_ADDRESS = ActionId.core("players.get-ip-address");
    public static final ActionId TELEPORT = ActionId.core("players.teleport");
    public static final ActionId HEAL = ActionId.core("players.heal");
    public static final ActionId FEED = ActionId.core("players.feed");
    public static final ActionId EXTINGUISH = ActionId.core("players.extinguish");
    public static final ActionId EFFECTS_CLEAR = ActionId.core("players.effects.clear");
    public static final ActionId EFFECTS_ADD = ActionId.core("players.effects.add");
    public static final ActionId EFFECTS_REMOVE = ActionId.core("players.effects.remove");
    public static final ActionId SET_HEALTH = ActionId.core("players.set-health");
    public static final ActionId SET_FOOD = ActionId.core("players.set-food");
    public static final ActionId SET_XP = ActionId.core("players.set-xp");
    public static final ActionId SET_LEVEL = ActionId.core("players.set-level");
    public static final ActionId FLY_TOGGLE = ActionId.core("players.fly.toggle");
    public static final ActionId FLY_SPEED = ActionId.core("players.fly.speed");
    public static final ActionId WALK_SPEED = ActionId.core("players.walk-speed");
    public static final ActionId GLOW_TOGGLE = ActionId.core("players.glow.toggle");
    public static final ActionId GRAVITY_TOGGLE = ActionId.core("players.gravity.toggle");
    public static final ActionId COLLISION_TOGGLE = ActionId.core("players.collision.toggle");
    public static final ActionId GAMEMODE = ActionId.core("players.gamemode");
    public static final ActionId INVENTORY_SET = ActionId.core("players.inventory.set");
    public static final ActionId INVENTORY_CLEAR = ActionId.core("players.inventory.clear");
    public static final ActionId ENDERCHEST_SET = ActionId.core("players.enderchest.set");
}

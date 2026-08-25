package dev.universaladmin.modules.worlds.action;

import dev.universaladmin.action.ActionId;

/** Every {@link ActionId} the Worlds module registers, in one place - mirrors {@code ServerActionIds}. */
public final class WorldActionIds {

    private WorldActionIds() {
    }

    public static final ActionId TELEPORT_TO_SPAWN = ActionId.core("worlds.teleport-to-spawn");
    public static final ActionId SET_SPAWN = ActionId.core("worlds.spawn.set");
    public static final ActionId SET_TIME = ActionId.core("worlds.time.set");
    public static final ActionId SET_WEATHER = ActionId.core("worlds.weather.set");
    public static final ActionId SET_DIFFICULTY = ActionId.core("worlds.difficulty.set");
    public static final ActionId SET_BORDER_CENTER = ActionId.core("worlds.border.center");
    public static final ActionId SET_BORDER_SIZE = ActionId.core("worlds.border.size");
    public static final ActionId SET_BORDER_DAMAGE = ActionId.core("worlds.border.damage");
    public static final ActionId SET_BORDER_WARNING = ActionId.core("worlds.border.warning");
    public static final ActionId SET_GAME_RULE = ActionId.core("worlds.gamerule.set");
}

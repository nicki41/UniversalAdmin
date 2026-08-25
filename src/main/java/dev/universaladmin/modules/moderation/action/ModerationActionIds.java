package dev.universaladmin.modules.moderation.action;

import dev.universaladmin.action.ActionId;

/**
 * Every {@link ActionId} the Moderation module registers, in one place -
 * {@link dev.universaladmin.modules.moderation.ModerationModule} builds each
 * {@link dev.universaladmin.action.ActionDefinition} under one of these, and
 * the GUI package calls {@link
 * dev.universaladmin.action.ActionExecutor#execute} against the same
 * constant instead of retyping the raw string.
 */
public final class ModerationActionIds {

    private ModerationActionIds() {
    }

    public static final ActionId KICK = ActionId.core("moderation.kick");
    public static final ActionId BAN = ActionId.core("moderation.ban");
    public static final ActionId TEMP_BAN = ActionId.core("moderation.tempban");
    public static final ActionId IP_BAN = ActionId.core("moderation.ipban");
    public static final ActionId MUTE = ActionId.core("moderation.mute");
    public static final ActionId TEMP_MUTE = ActionId.core("moderation.tempmute");
    public static final ActionId WARN = ActionId.core("moderation.warn");
    public static final ActionId UNBAN = ActionId.core("moderation.unban");
    public static final ActionId UNMUTE = ActionId.core("moderation.unmute");
    public static final ActionId REMOVE_WARN = ActionId.core("moderation.removewarn");
    public static final ActionId FREEZE = ActionId.core("moderation.freeze");
    public static final ActionId UNFREEZE = ActionId.core("moderation.unfreeze");
    public static final ActionId VANISH = ActionId.core("moderation.vanish");
    public static final ActionId GODMODE = ActionId.core("moderation.godmode");
    public static final ActionId NO_COLLISION = ActionId.core("moderation.no-collision");
    public static final ActionId STAFF_MODE_ENTER = ActionId.core("moderation.staffmode.enter");
    public static final ActionId STAFF_MODE_EXIT = ActionId.core("moderation.staffmode.exit");
    public static final ActionId STAFF_MODE_RECOVER = ActionId.core("moderation.staffmode.recover");
    public static final ActionId FREEZE_DISCONNECT_NOTICE = ActionId.core("moderation.freeze.disconnect-notice");
}

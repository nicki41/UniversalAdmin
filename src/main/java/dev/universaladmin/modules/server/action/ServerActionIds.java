package dev.universaladmin.modules.server.action;

import dev.universaladmin.action.ActionId;

/** Every {@link ActionId} the Server module registers, in one place - mirrors {@code ModerationActionIds}. */
public final class ServerActionIds {

    private ServerActionIds() {
    }

    public static final ActionId BROADCAST_MESSAGE = ActionId.core("server.broadcast.message");
    public static final ActionId BROADCAST_TITLE = ActionId.core("server.broadcast.title");
    public static final ActionId BROADCAST_ACTIONBAR = ActionId.core("server.broadcast.actionbar");
    public static final ActionId MAINTENANCE_ENABLE = ActionId.core("server.maintenance.enable");
    public static final ActionId MAINTENANCE_DISABLE = ActionId.core("server.maintenance.disable");
    public static final ActionId MAINTENANCE_SET_ALLOWED_PLAYERS = ActionId.core("server.maintenance.allowed-players");
    public static final ActionId SHUTDOWN = ActionId.core("server.shutdown");
    public static final ActionId CANCEL_SHUTDOWN = ActionId.core("server.shutdown.cancel");
    public static final ActionId RESTART = ActionId.core("server.restart");
    public static final ActionId CANCEL_RESTART = ActionId.core("server.restart.cancel");
}

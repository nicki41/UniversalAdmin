package dev.universaladmin.modules.server;

import dev.universaladmin.permission.PermissionDefault;
import dev.universaladmin.permission.PermissionDefinition;
import dev.universaladmin.permission.PermissionNode;
import dev.universaladmin.permission.PermissionRegistry;
import java.util.List;

/**
 * Every {@link PermissionNode} the Server module declares, in one place -
 * see docs/user/permissions.md for the documented table these mirror.
 */
public final class ServerPermissions {

    private ServerPermissions() {
    }

    public static final PermissionNode VIEW = PermissionNode.core("server.view");
    public static final PermissionNode BROADCAST = PermissionNode.core("server.broadcast");
    public static final PermissionNode MAINTENANCE = PermissionNode.core("server.maintenance");
    public static final PermissionNode RESTART = PermissionNode.core("server.restart");
    public static final PermissionNode SHUTDOWN = PermissionNode.core("server.shutdown");

    /** Not under the {@code server} namespace on purpose - mirrors {@code ModerationPermissions.BYPASS_VANISH}'s precedent. */
    public static final PermissionNode BYPASS_MAINTENANCE = PermissionNode.core("bypass.maintenance");

    public static void registerAll(PermissionRegistry registry) {
        List.of(
                new PermissionDefinition(VIEW, "View the server dashboard", PermissionDefault.OP),
                new PermissionDefinition(BROADCAST, "Broadcast messages/titles/actionbars to every online player", PermissionDefault.OP),
                new PermissionDefinition(MAINTENANCE, "Enable/disable maintenance mode and manage its allow-list", PermissionDefault.OP),
                new PermissionDefinition(RESTART, "Restart the server (with confirmation/countdown)", PermissionDefault.OP),
                new PermissionDefinition(SHUTDOWN, "Shut down the server (with confirmation/countdown)", PermissionDefault.OP),
                new PermissionDefinition(BYPASS_MAINTENANCE, "Join the server while maintenance mode is enabled", PermissionDefault.OP))
                .forEach(registry::register);
    }
}

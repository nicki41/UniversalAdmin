package dev.universaladmin.modules.network;

import dev.universaladmin.permission.PermissionDefault;
import dev.universaladmin.permission.PermissionDefinition;
import dev.universaladmin.permission.PermissionNode;
import java.util.List;

/** Permission nodes of the Network module. */
public final class NetworkPermissions {

    /** See the network menu, {@code /admin network} and {@code /admin find}. */
    public static final PermissionNode VIEW = PermissionNode.core("network.view");
    /** Go to another server, or to a player on another server. */
    public static final PermissionNode GOTO = PermissionNode.core("network.goto");
    /** Move other players between servers (send, bring). */
    public static final PermissionNode SEND = PermissionNode.core("network.send");
    /** Broadcast an alert on every server. */
    public static final PermissionNode ALERT = PermissionNode.core("network.alert");

    private NetworkPermissions() {
    }

    static List<PermissionDefinition> all() {
        return List.of(
                new PermissionDefinition(VIEW, "See all servers of the network and where players are", PermissionDefault.OP),
                new PermissionDefinition(GOTO, "Go to another server or to a player on another server", PermissionDefault.OP),
                new PermissionDefinition(SEND, "Move other players between servers", PermissionDefault.OP),
                new PermissionDefinition(ALERT, "Broadcast an alert on every server of the network", PermissionDefault.OP));
    }
}

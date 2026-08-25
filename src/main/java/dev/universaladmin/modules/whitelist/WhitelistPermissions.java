package dev.universaladmin.modules.whitelist;

import dev.universaladmin.permission.PermissionDefault;
import dev.universaladmin.permission.PermissionDefinition;
import dev.universaladmin.permission.PermissionNode;
import dev.universaladmin.permission.PermissionRegistry;
import java.util.List;

/** Every {@link PermissionNode} the Whitelist module declares - the exact five nodes given in the spec. */
public final class WhitelistPermissions {

    private WhitelistPermissions() {
    }

    public static final PermissionNode VIEW = PermissionNode.core("whitelist.view");
    public static final PermissionNode TOGGLE = PermissionNode.core("whitelist.toggle");
    public static final PermissionNode ADD = PermissionNode.core("whitelist.add");
    public static final PermissionNode REMOVE = PermissionNode.core("whitelist.remove");
    /** Gates setting an expiration on an add - separate from {@link #ADD} so an admin can add without being allowed to make an entry temporary. */
    public static final PermissionNode TEMPORARY = PermissionNode.core("whitelist.temporary");

    public static void registerAll(PermissionRegistry registry) {
        List.of(
                new PermissionDefinition(VIEW, "View the whitelist status and members", PermissionDefault.OP),
                new PermissionDefinition(TOGGLE, "Enable/disable the whitelist", PermissionDefault.OP),
                new PermissionDefinition(ADD, "Add a player to the whitelist", PermissionDefault.OP),
                new PermissionDefinition(REMOVE, "Remove a player from the whitelist", PermissionDefault.OP),
                new PermissionDefinition(TEMPORARY, "Give a whitelist entry an expiration", PermissionDefault.OP))
                .forEach(registry::register);
    }
}

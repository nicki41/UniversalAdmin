package dev.universaladmin.command.builtin;

import dev.universaladmin.permission.PermissionDefault;
import dev.universaladmin.permission.PermissionDefinition;
import dev.universaladmin.permission.PermissionNode;
import dev.universaladmin.permission.PermissionRegistry;

/** Permission nodes for the core's own {@code /admin} subcommands (status, modules, help, ...). */
public final class CorePermissions {

    public static final PermissionNode MENU = PermissionNode.core("menu.open");
    public static final PermissionNode STATUS = PermissionNode.core("status");
    public static final PermissionNode RELOAD = PermissionNode.core("reload");
    public static final PermissionNode UPDATE_APPLY = PermissionNode.core("update.apply");

    private CorePermissions() {
    }

    /** Registers the nodes that are not already registered by bootstrap itself. */
    public static void registerAll(PermissionRegistry registry) {
        registry.register(new PermissionDefinition(
                STATUS, "View UniversalAdmin's version, uptime and module status", PermissionDefault.OP));
    }
}

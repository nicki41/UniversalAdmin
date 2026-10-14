package dev.universaladmin.modules.settings;

import dev.universaladmin.permission.PermissionDefault;
import dev.universaladmin.permission.PermissionDefinition;
import dev.universaladmin.permission.PermissionNode;

/** Permission nodes of the Settings module. */
public final class SettingsPermissions {

    /** View and change settings in-game (GUI and {@code /admin settings}); sensitive settings stay file-only. */
    public static final PermissionNode MANAGE = PermissionNode.core("settings.manage");

    private SettingsPermissions() {
    }

    static PermissionDefinition manage() {
        return new PermissionDefinition(MANAGE, "View and change UniversalAdmin settings in-game", PermissionDefault.OP);
    }
}

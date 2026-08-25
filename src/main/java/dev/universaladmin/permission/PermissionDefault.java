package dev.universaladmin.permission;

/**
 * Who gets a permission by default, absent any permission plugin. Mirrors
 * Bukkit's {@code org.bukkit.permissions.PermissionDefault} deliberately:
 * this package stays free of Paper imports so permission definitions can be
 * unit-tested, and the bootstrap layer maps this enum onto Bukkit's when it
 * syncs definitions into the server's {@code PluginManager}.
 */
public enum PermissionDefault {
    TRUE,
    FALSE,
    OP,
    NOT_OP
}

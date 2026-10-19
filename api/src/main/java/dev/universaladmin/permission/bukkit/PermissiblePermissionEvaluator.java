package dev.universaladmin.permission.bukkit;

import dev.universaladmin.permission.PermissionEvaluator;
import dev.universaladmin.permission.PermissionNode;
import org.bukkit.permissions.Permissible;

/**
 * Bukkit-backed {@link PermissionEvaluator}: delegates to {@link Permissible#hasPermission},
 * so a {@code Player} or {@code ConsoleCommandSender} (both implement
 * {@code Permissible}) works unchanged, including wildcard resolution by
 * whichever permission plugin is installed. Lives in this {@code .bukkit}
 * subpackage rather than {@code dev.universaladmin.permission} so that
 * package itself stays free of Paper imports - see {@code storage}/{@code storage.jdbc}
 * for the same adapter-subpackage convention.
 */
public final class PermissiblePermissionEvaluator implements PermissionEvaluator {

    private final Permissible permissible;

    public PermissiblePermissionEvaluator(Permissible permissible) {
        this.permissible = permissible;
    }

    @Override
    public boolean has(PermissionNode node) {
        return permissible.hasPermission(node.value());
    }
}

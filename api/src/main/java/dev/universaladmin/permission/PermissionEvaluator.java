package dev.universaladmin.permission;

/**
 * Resolves whether a single {@link PermissionNode} is granted, without the
 * caller needing to know who or what is being asked - a Bukkit
 * {@code Permissible}, an always-trusted console/system actor, or (later) a
 * web session's resolved permission set. This is the "Permission Resolver"
 * every actor carries instead of code reaching for {@code player.hasPermission(...)}
 * directly - see {@link dev.universaladmin.action.Actor#hasPermission}.
 *
 * <p>Wildcard resolution (e.g. a permission plugin granting
 * {@code universaladmin.*}) is not implemented here - {@link
 * dev.universaladmin.permission.bukkit.PermissiblePermissionEvaluator} just
 * delegates to Bukkit's own {@code Permissible.hasPermission}, so wildcards
 * work exactly as they do for any other plugin, via whichever permission
 * plugin (LuckPerms and friends) the server runs.
 */
@FunctionalInterface
public interface PermissionEvaluator {

    boolean has(PermissionNode node);

    /** For actors that are always trusted (console, system/scheduled tasks). */
    static PermissionEvaluator allowAll() {
        return node -> true;
    }

    /** For actors with no meaningful permission set - e.g. a rehydrated audit-log actor. */
    static PermissionEvaluator denyAll() {
        return node -> false;
    }
}

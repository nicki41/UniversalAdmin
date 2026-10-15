package dev.universaladmin.bootstrap;

import dev.universaladmin.permission.PermissionDefinition;
import dev.universaladmin.permission.PermissionRegistry;
import java.util.HashSet;
import java.util.Set;
import org.bukkit.permissions.Permission;
import org.bukkit.plugin.PluginManager;

/**
 * Keeps Bukkit's permission list in line with {@link PermissionRegistry} -
 * so permission plugins (LuckPerms & co.) can list and auto-complete every
 * UniversalAdmin node, including nodes an extension registered after
 * startup. Only ever touches permissions it added itself.
 */
final class BukkitPermissionSync {

    private final PluginManager pluginManager;
    private final PermissionRegistry registry;
    private final Set<String> synced = new HashSet<>();

    BukkitPermissionSync(PluginManager pluginManager, PermissionRegistry registry) {
        this.pluginManager = pluginManager;
        this.registry = registry;
    }

    /** Adds every registered node Bukkit doesn't know yet, and removes the ones whose definition is gone. */
    synchronized void sync() {
        Set<String> current = new HashSet<>();
        for (PermissionDefinition definition : registry.all()) {
            String node = definition.node().value();
            current.add(node);
            if (pluginManager.getPermission(node) == null) {
                pluginManager.addPermission(new Permission(node, definition.description(), map(definition.defaultValue())));
                synced.add(node);
            }
        }
        for (String node : Set.copyOf(synced)) {
            if (!current.contains(node)) {
                pluginManager.removePermission(node);
                synced.remove(node);
            }
        }
    }

    private static org.bukkit.permissions.PermissionDefault map(dev.universaladmin.permission.PermissionDefault value) {
        return switch (value) {
            case TRUE -> org.bukkit.permissions.PermissionDefault.TRUE;
            case FALSE -> org.bukkit.permissions.PermissionDefault.FALSE;
            case OP -> org.bukkit.permissions.PermissionDefault.OP;
            case NOT_OP -> org.bukkit.permissions.PermissionDefault.NOT_OP;
        };
    }
}

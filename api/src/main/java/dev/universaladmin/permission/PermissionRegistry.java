package dev.universaladmin.permission;

import dev.universaladmin.core.registry.Registry;
import dev.universaladmin.core.registry.SimpleRegistry;
import java.util.Optional;

/**
 * Registry of every {@link PermissionDefinition} known to the platform.
 *
 * <p>This registry only tracks definitions; it does not talk to Bukkit's
 * {@code PluginManager}. {@code dev.universaladmin.bootstrap} reads
 * {@link #all()} once modules have registered and syncs the definitions into
 * the server at that point, so this class stays testable without a running
 * server.
 */
public final class PermissionRegistry {

    private final Registry<PermissionNode, PermissionDefinition> delegate = new SimpleRegistry<>();

    public void register(PermissionDefinition definition) {
        delegate.register(definition.node(), definition);
    }

    /**
     * For a module's disable cleanup - see
     * {@link dev.universaladmin.module.ModuleResources}. Note this only
     * removes the definition from this registry; it does not retract an
     * already-synced permission from Bukkit's {@code PluginManager} (the
     * sync in {@code UniversalAdminPlugin} runs once, at startup).
     */
    public void unregister(PermissionNode node) {
        delegate.unregister(node);
    }

    public Optional<PermissionDefinition> get(PermissionNode node) {
        return delegate.get(node);
    }

    public Iterable<PermissionDefinition> all() {
        return delegate.all();
    }
}

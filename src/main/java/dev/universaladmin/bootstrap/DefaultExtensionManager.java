package dev.universaladmin.bootstrap;

import dev.universaladmin.extension.ExtensionInfo;
import dev.universaladmin.extension.ExtensionManager;
import dev.universaladmin.module.Module;
import dev.universaladmin.module.ModuleId;
import dev.universaladmin.module.ModuleManager;
import dev.universaladmin.module.ModuleRegistry;
import dev.universaladmin.module.ModuleState;
import dev.universaladmin.storage.MigrationRunner;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.Plugin;

/**
 * The {@link ExtensionManager} behind {@code UniversalAdmin#extensions()}:
 * enables an extension's module immediately (applying its migrations
 * between {@code onLoad} and {@code onEnable}, the same order the built-in
 * modules get at startup), syncs its permissions to Bukkit, and disables it
 * again when the owning plugin is disabled.
 */
final class DefaultExtensionManager implements ExtensionManager, Listener {

    private static final String RESERVED_NAMESPACE = "core";

    private final ModuleRegistry registry;
    private final MigrationRunner migrations;
    private final BukkitPermissionSync permissions;
    private final Logger logger;
    private final Map<ModuleId, Plugin> owners = new ConcurrentHashMap<>();
    private volatile ModuleManager modules;

    DefaultExtensionManager(ModuleRegistry registry, MigrationRunner migrations, BukkitPermissionSync permissions, Logger logger) {
        this.registry = registry;
        this.migrations = migrations;
        this.permissions = permissions;
        this.logger = logger;
    }

    /** Called once the platform's {@link ModuleManager} exists; registrations are refused before that. */
    void attach(ModuleManager moduleManager) {
        this.modules = moduleManager;
    }

    @Override
    public boolean register(Plugin owner, Module module) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(module, "module");
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("Extension modules must be registered on the main thread");
        }
        ModuleManager manager = modules;
        if (manager == null) {
            throw new IllegalStateException("UniversalAdmin is not enabled yet - register modules from your plugin's onEnable");
        }
        ModuleId id = module.descriptor().id();
        if (RESERVED_NAMESPACE.equals(id.key().namespace())) {
            throw new IllegalArgumentException("Extension module " + id + " uses the reserved namespace '"
                    + RESERVED_NAMESPACE + "' - use your own, e.g. " + owner.getName().toLowerCase(java.util.Locale.ROOT) + ":"
                    + id.key().name());
        }
        logger.info(() -> "Enabling module " + id + " from extension " + owner.getName() + " " + owner.getPluginMeta().getVersion());
        // Migrations registered in the module's onLoad are applied before its
        // onEnable - synchronously, like at startup; an extension enabling
        // during server start runs before players can join.
        ModuleState state = manager.loadAndEnable(module, migrations::runPending);
        owners.put(id, owner);
        permissions.sync();
        return state == ModuleState.ENABLED;
    }

    @Override
    public List<ExtensionInfo> extensions() {
        List<ExtensionInfo> list = new ArrayList<>();
        owners.forEach((id, owner) -> list.add(new ExtensionInfo(owner.getName(), owner.getPluginMeta().getVersion(), id,
                registry.get(id).isPresent() ? registry.state(id) : ModuleState.DISABLED)));
        list.sort(java.util.Comparator.comparing(info -> info.moduleId().toString()));
        return list;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPluginDisable(PluginDisableEvent event) {
        ModuleManager manager = modules;
        if (manager == null) {
            return;
        }
        boolean changed = false;
        for (Map.Entry<ModuleId, Plugin> entry : List.copyOf(owners.entrySet())) {
            if (entry.getValue().equals(event.getPlugin())) {
                manager.disable(entry.getKey());
                owners.remove(entry.getKey());
                changed = true;
            }
        }
        if (changed) {
            permissions.sync();
        }
    }
}

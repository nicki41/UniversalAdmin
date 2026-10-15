package dev.universaladmin.module;

import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Lifecycle-aware resource tracking for a single module. One instance lives
 * on each module's {@link ModuleContext}, created before {@link Module#onEnable}
 * runs and released by {@link ModuleManager} after {@link Module#onDisable} -
 * or immediately, if {@code onEnable} itself throws, so a module that fails
 * halfway through registering listeners never leaks them.
 *
 * <p>A module should register anything here that must not outlive it:
 *
 * <pre>{@code
 * context.resources().listener(new MyJoinListener(playerService));
 * context.resources().task(myRepeatingTask);
 * context.resources().closeable(() -> context.platform().actions().unregister(MY_ACTION_ID));
 * }</pre>
 *
 * Resources are released in reverse registration order, and one resource
 * failing to close does not stop the rest from being released.
 */
public final class ModuleResources {

    private final Plugin plugin;
    private final ModuleId owner;
    private final ConcurrentLinkedDeque<AutoCloseable> cleanupTasks = new ConcurrentLinkedDeque<>();

    ModuleResources(Plugin plugin, ModuleId owner) {
        this.plugin = plugin;
        this.owner = owner;
    }

    /** Registers a Bukkit listener and arranges for it to be unregistered on disable. */
    public void listener(Listener listener) {
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        track(() -> HandlerList.unregisterAll(listener));
    }

    /** Arranges for a scheduled task to be cancelled on disable. */
    public void task(BukkitTask task) {
        track(task::cancel);
    }

    /** Arranges for an arbitrary resource (a registry entry, a cache, ...) to be released on disable. */
    public void closeable(AutoCloseable closeable) {
        track(closeable);
    }

    private void track(AutoCloseable closeable) {
        cleanupTasks.addFirst(closeable);
    }

    /** Releases every tracked resource, most-recently-registered first. Never throws. */
    void closeAll(Logger logger) {
        AutoCloseable closeable;
        while ((closeable = cleanupTasks.pollFirst()) != null) {
            try {
                closeable.close();
            } catch (Exception e) {
                logger.log(Level.WARNING, "Failed to release a resource for module " + owner, e);
            }
        }
    }
}

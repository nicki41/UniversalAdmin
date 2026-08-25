package dev.universaladmin.scheduler;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.bukkit.plugin.Plugin;

/**
 * Default {@link TaskScheduler}. Background work runs on a virtual-thread
 * executor (stable since Java 21), which suits blocking JDBC calls well:
 * each database call gets its own cheap thread instead of contending for a
 * small fixed pool. Main-thread hops go through Bukkit's own scheduler.
 */
public final class PaperTaskScheduler implements TaskScheduler {

    private final Plugin plugin;
    private final ExecutorService backgroundExecutor = Executors.newVirtualThreadPerTaskExecutor();

    public PaperTaskScheduler(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public <T> CompletableFuture<T> supplyAsync(Supplier<T> task) {
        return CompletableFuture.supplyAsync(task, backgroundExecutor);
    }

    @Override
    public CompletableFuture<Void> runAsync(Runnable task) {
        return CompletableFuture.runAsync(task, backgroundExecutor);
    }

    @Override
    public void runOnMainThread(Runnable task) {
        plugin.getServer().getScheduler().runTask(plugin, task);
    }

    @Override
    public void close() {
        backgroundExecutor.shutdown();
        try {
            if (!backgroundExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                backgroundExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            backgroundExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}

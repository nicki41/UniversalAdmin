package dev.universaladmin.scheduler;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * The only sanctioned way to cross between the Paper main thread and
 * background work in UniversalAdmin. See docs/architecture/threading.md.
 *
 * <p>Rule of thumb: database and other blocking IO happens in
 * {@link #supplyAsync}/{@link #runAsync}; anything that touches Bukkit API
 * (inventories, entities, the world) happens in {@link #runOnMainThread}.
 * Repositories and services never call Bukkit scheduler methods directly.
 */
public interface TaskScheduler {

    <T> CompletableFuture<T> supplyAsync(Supplier<T> task);

    CompletableFuture<Void> runAsync(Runnable task);

    /** Runs {@code task} on the Paper main thread on the next tick. */
    void runOnMainThread(Runnable task);

    /** Shuts down the background executor. Called once from plugin disable. */
    void close();
}

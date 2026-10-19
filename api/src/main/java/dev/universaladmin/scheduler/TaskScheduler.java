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

    /**
     * Runs {@code task} on the main thread and completes the returned future
     * with its result (or its exception) - for an action that must read
     * Bukkit state, then continue asynchronously.
     */
    default <T> CompletableFuture<T> supplyOnMainThread(Supplier<T> task) {
        CompletableFuture<T> future = new CompletableFuture<>();
        runOnMainThread(() -> {
            try {
                future.complete(task.get());
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });
        return future;
    }
}

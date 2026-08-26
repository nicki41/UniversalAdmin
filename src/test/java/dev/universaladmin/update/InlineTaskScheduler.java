package dev.universaladmin.update;

import dev.universaladmin.scheduler.TaskScheduler;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/** Runs everything on the calling thread - same shape as {@code telemetry.InlineTaskScheduler}, duplicated since that one is package-private there. */
final class InlineTaskScheduler implements TaskScheduler {

    private int mainThreadHops;
    private int asyncTasks;

    @Override
    public <T> CompletableFuture<T> supplyAsync(Supplier<T> task) {
        asyncTasks++;
        return CompletableFuture.completedFuture(task.get());
    }

    @Override
    public CompletableFuture<Void> runAsync(Runnable task) {
        asyncTasks++;
        task.run();
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void runOnMainThread(Runnable task) {
        mainThreadHops++;
        task.run();
    }

    @Override
    public void close() {
    }

    int mainThreadHops() {
        return mainThreadHops;
    }

    int asyncTasks() {
        return asyncTasks;
    }
}

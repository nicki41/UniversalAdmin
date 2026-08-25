package dev.universaladmin.telemetry;

import dev.universaladmin.scheduler.TaskScheduler;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * A {@link TaskScheduler} that runs everything on the calling thread, so a
 * test can assert on the result of {@code sendHeartbeat()} without waiting
 * for threads. Also counts the main-thread hops, which is how
 * {@code TelemetryServiceTest} proves a disabled subsystem does no work at
 * all rather than merely dropping the payload at the end.
 */
final class InlineTaskScheduler implements TaskScheduler {

    private int mainThreadHops;
    private int asyncTasks;
    private boolean closed;

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
        closed = true;
    }

    int mainThreadHops() {
        return mainThreadHops;
    }

    int asyncTasks() {
        return asyncTasks;
    }

    boolean isClosed() {
        return closed;
    }
}

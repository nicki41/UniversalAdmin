package dev.universaladmin.modules.server.action;

import dev.universaladmin.action.Action;
import dev.universaladmin.action.ActionContext;
import dev.universaladmin.action.ActionId;
import dev.universaladmin.action.ActionResult;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.modules.server.ServerLifecycleService;
import dev.universaladmin.scheduler.TaskScheduler;
import java.util.concurrent.CompletableFuture;

/**
 * Starts the shutdown confirmation countdown (or shuts down immediately if
 * disabled) - see {@link ServerLifecycleService}. {@code reason} may be
 * {@code null}; raw {@code String} input rather than a wrapper record, like
 * {@link BroadcastMessageAction}, so {@code UniversalAdminCommand} can invoke
 * it directly.
 */
public final class ShutdownAction implements Action<String, Void> {

    public static final ActionId ID = ServerActionIds.SHUTDOWN;

    private final TaskScheduler scheduler;
    private final ServerLifecycleService lifecycleService;

    public ShutdownAction(TaskScheduler scheduler, ServerLifecycleService lifecycleService) {
        this.scheduler = scheduler;
        this.lifecycleService = lifecycleService;
    }

    @Override
    public ActionId id() {
        return ID;
    }

    @Override
    public CompletableFuture<ActionResult<Void>> execute(ActionContext context, String reason) {
        CompletableFuture<ActionResult<Void>> future = new CompletableFuture<>();
        scheduler.runOnMainThread(() -> {
            boolean started = lifecycleService.scheduleShutdown(reason);
            future.complete(started
                    ? ActionResult.success(null)
                    : ActionResult.failure(ActionResult.FailureReason.CONFLICT, MessageKey.of("server.action.already-pending")));
        });
        return future;
    }
}

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
 * Starts the restart confirmation countdown (or restarts immediately if
 * disabled) - see {@link ServerLifecycleService}'s javadoc for what
 * "restart" actually does and its platform limitations. Raw {@code String}
 * input - see {@link ShutdownAction}'s javadoc.
 */
public final class RestartAction implements Action<String, Void> {

    public static final ActionId ID = ServerActionIds.RESTART;

    private final TaskScheduler scheduler;
    private final ServerLifecycleService lifecycleService;

    public RestartAction(TaskScheduler scheduler, ServerLifecycleService lifecycleService) {
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
            boolean started = lifecycleService.scheduleRestart(reason);
            future.complete(started
                    ? ActionResult.success(null)
                    : ActionResult.failure(ActionResult.FailureReason.CONFLICT, MessageKey.of("server.action.already-pending")));
        });
        return future;
    }
}

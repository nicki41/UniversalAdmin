package dev.universaladmin.modules.server.action;

import dev.universaladmin.action.Action;
import dev.universaladmin.action.ActionContext;
import dev.universaladmin.action.ActionId;
import dev.universaladmin.action.ActionResult;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.modules.server.ServerLifecycleService;
import dev.universaladmin.scheduler.TaskScheduler;
import java.util.concurrent.CompletableFuture;

/** Aborts a pending shutdown countdown - a countdown with no way to abort is a footgun, see {@code ServerModule}'s javadoc. */
public final class CancelShutdownAction implements Action<Void, Void> {

    public static final ActionId ID = ServerActionIds.CANCEL_SHUTDOWN;

    private final TaskScheduler scheduler;
    private final ServerLifecycleService lifecycleService;

    public CancelShutdownAction(TaskScheduler scheduler, ServerLifecycleService lifecycleService) {
        this.scheduler = scheduler;
        this.lifecycleService = lifecycleService;
    }

    @Override
    public ActionId id() {
        return ID;
    }

    @Override
    public CompletableFuture<ActionResult<Void>> execute(ActionContext context, Void input) {
        CompletableFuture<ActionResult<Void>> future = new CompletableFuture<>();
        scheduler.runOnMainThread(() -> {
            boolean matches = lifecycleService.pending().action() == ServerLifecycleService.PendingAction.SHUTDOWN;
            boolean cancelled = matches && lifecycleService.cancel();
            future.complete(cancelled
                    ? ActionResult.success(null)
                    : ActionResult.failure(ActionResult.FailureReason.NOT_FOUND, MessageKey.of("server.action.nothing-pending")));
        });
        return future;
    }
}

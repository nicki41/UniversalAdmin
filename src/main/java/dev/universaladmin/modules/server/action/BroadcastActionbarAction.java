package dev.universaladmin.modules.server.action;

import dev.universaladmin.action.Action;
import dev.universaladmin.action.ActionContext;
import dev.universaladmin.action.ActionId;
import dev.universaladmin.action.ActionResult;
import dev.universaladmin.localization.ComponentMessages;
import dev.universaladmin.notification.NotificationService;
import dev.universaladmin.scheduler.TaskScheduler;
import java.util.concurrent.CompletableFuture;

/** Shows a message in the action bar of every online player. Raw {@code String} input - see {@link BroadcastMessageAction}'s javadoc. */
public final class BroadcastActionbarAction implements Action<String, Void> {

    public static final ActionId ID = ServerActionIds.BROADCAST_ACTIONBAR;

    private final TaskScheduler scheduler;
    private final NotificationService notifications;

    public BroadcastActionbarAction(TaskScheduler scheduler, NotificationService notifications) {
        this.scheduler = scheduler;
        this.notifications = notifications;
    }

    @Override
    public ActionId id() {
        return ID;
    }

    @Override
    public CompletableFuture<ActionResult<Void>> execute(ActionContext context, String message) {
        CompletableFuture<ActionResult<Void>> future = new CompletableFuture<>();
        scheduler.runOnMainThread(() -> {
            notifications.broadcastActionBar(ComponentMessages.render(message));
            future.complete(ActionResult.success(null));
        });
        return future;
    }
}

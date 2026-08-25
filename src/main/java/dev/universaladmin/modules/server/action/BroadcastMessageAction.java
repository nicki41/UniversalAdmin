package dev.universaladmin.modules.server.action;

import dev.universaladmin.action.Action;
import dev.universaladmin.action.ActionContext;
import dev.universaladmin.action.ActionId;
import dev.universaladmin.action.ActionResult;
import dev.universaladmin.localization.ComponentMessages;
import dev.universaladmin.notification.NotificationService;
import dev.universaladmin.scheduler.TaskScheduler;
import java.util.concurrent.CompletableFuture;

/**
 * Sends a chat message to every online player - admin-authored text, rendered
 * as MiniMessage (see {@link ComponentMessages}). Takes a raw {@code String}
 * rather than a wrapper record, like {@code RecoverStaffSnapshotAction}
 * takes a raw {@code UUID} - so {@code UniversalAdminCommand} can invoke it
 * without importing anything from this module (see that class's javadoc).
 */
public final class BroadcastMessageAction implements Action<String, Void> {

    public static final ActionId ID = ServerActionIds.BROADCAST_MESSAGE;

    private final TaskScheduler scheduler;
    private final NotificationService notifications;

    public BroadcastMessageAction(TaskScheduler scheduler, NotificationService notifications) {
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
            notifications.broadcast(ComponentMessages.render(message));
            future.complete(ActionResult.success(null));
        });
        return future;
    }
}

package dev.universaladmin.modules.server.action;

import dev.universaladmin.action.Action;
import dev.universaladmin.action.ActionContext;
import dev.universaladmin.action.ActionId;
import dev.universaladmin.action.ActionResult;
import dev.universaladmin.localization.ComponentMessages;
import dev.universaladmin.notification.NotificationService;
import dev.universaladmin.scheduler.TaskScheduler;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.Component;

/** Shows a title/subtitle to every online player. */
public final class BroadcastTitleAction implements Action<BroadcastTitleInput, Void> {

    public static final ActionId ID = ServerActionIds.BROADCAST_TITLE;

    private static final Duration FADE_IN = Duration.ofMillis(500);
    private static final Duration STAY = Duration.ofSeconds(4);
    private static final Duration FADE_OUT = Duration.ofSeconds(1);

    private final TaskScheduler scheduler;
    private final NotificationService notifications;

    public BroadcastTitleAction(TaskScheduler scheduler, NotificationService notifications) {
        this.scheduler = scheduler;
        this.notifications = notifications;
    }

    @Override
    public ActionId id() {
        return ID;
    }

    @Override
    public CompletableFuture<ActionResult<Void>> execute(ActionContext context, BroadcastTitleInput input) {
        CompletableFuture<ActionResult<Void>> future = new CompletableFuture<>();
        scheduler.runOnMainThread(() -> {
            Component title = ComponentMessages.render(input.title());
            Component subtitle = input.subtitle() == null || input.subtitle().isBlank()
                    ? Component.empty() : ComponentMessages.render(input.subtitle());
            notifications.broadcastTitle(title, subtitle, FADE_IN, STAY, FADE_OUT);
            future.complete(ActionResult.success(null));
        });
        return future;
    }
}

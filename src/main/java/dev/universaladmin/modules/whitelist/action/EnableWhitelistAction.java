package dev.universaladmin.modules.whitelist.action;

import dev.universaladmin.action.Action;
import dev.universaladmin.action.ActionContext;
import dev.universaladmin.action.ActionId;
import dev.universaladmin.action.ActionResult;
import dev.universaladmin.scheduler.TaskScheduler;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;

/** Turns the native whitelist on. A single boolean flag - no service layer needed, same style as {@code ShutdownAction}. */
public final class EnableWhitelistAction implements Action<Void, Void> {

    public static final ActionId ID = WhitelistActionIds.ENABLE;

    private final TaskScheduler scheduler;

    public EnableWhitelistAction(TaskScheduler scheduler) {
        this.scheduler = scheduler;
    }

    @Override
    public ActionId id() {
        return ID;
    }

    @Override
    public CompletableFuture<ActionResult<Void>> execute(ActionContext context, Void input) {
        CompletableFuture<ActionResult<Void>> future = new CompletableFuture<>();
        scheduler.runOnMainThread(() -> {
            Bukkit.setWhitelist(true);
            future.complete(ActionResult.success(null));
        });
        return future;
    }
}

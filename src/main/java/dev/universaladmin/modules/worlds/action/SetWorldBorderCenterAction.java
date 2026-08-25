package dev.universaladmin.modules.worlds.action;

import dev.universaladmin.action.Action;
import dev.universaladmin.action.ActionContext;
import dev.universaladmin.action.ActionId;
import dev.universaladmin.action.ActionResult;
import dev.universaladmin.scheduler.TaskScheduler;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.bukkit.World;

public final class SetWorldBorderCenterAction implements Action<SetWorldBorderCenterInput, Void> {

    public static final ActionId ID = WorldActionIds.SET_BORDER_CENTER;

    private final TaskScheduler scheduler;

    public SetWorldBorderCenterAction(TaskScheduler scheduler) {
        this.scheduler = scheduler;
    }

    @Override
    public ActionId id() {
        return ID;
    }

    @Override
    public CompletableFuture<ActionResult<Void>> execute(ActionContext context, SetWorldBorderCenterInput input) {
        CompletableFuture<ActionResult<Void>> future = new CompletableFuture<>();
        scheduler.runOnMainThread(() -> {
            World world = Bukkit.getWorld(input.worldName());
            if (world == null) {
                future.complete(WorldActionSupport.worldNotFound());
                return;
            }
            world.getWorldBorder().setCenter(input.x(), input.z());
            future.complete(ActionResult.success(null));
        });
        return future;
    }
}

package dev.universaladmin.modules.worlds.action;

import dev.universaladmin.action.Action;
import dev.universaladmin.action.ActionContext;
import dev.universaladmin.action.ActionId;
import dev.universaladmin.action.ActionResult;
import dev.universaladmin.scheduler.TaskScheduler;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldBorder;

public final class SetWorldBorderDamageAction implements Action<SetWorldBorderDamageInput, Void> {

    public static final ActionId ID = WorldActionIds.SET_BORDER_DAMAGE;

    private final TaskScheduler scheduler;

    public SetWorldBorderDamageAction(TaskScheduler scheduler) {
        this.scheduler = scheduler;
    }

    @Override
    public ActionId id() {
        return ID;
    }

    @Override
    public CompletableFuture<ActionResult<Void>> execute(ActionContext context, SetWorldBorderDamageInput input) {
        CompletableFuture<ActionResult<Void>> future = new CompletableFuture<>();
        scheduler.runOnMainThread(() -> {
            World world = Bukkit.getWorld(input.worldName());
            if (world == null) {
                future.complete(WorldActionSupport.worldNotFound());
                return;
            }
            WorldBorder border = world.getWorldBorder();
            border.setDamageAmount(input.amount());
            border.setDamageBuffer(input.buffer());
            future.complete(ActionResult.success(null));
        });
        return future;
    }
}

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

/** {@code timeSeconds} is converted to ticks (20/s) for {@link WorldBorder#setWarningTimeTicks(int)} - the seconds-based overload is deprecated for removal. */
public final class SetWorldBorderWarningAction implements Action<SetWorldBorderWarningInput, Void> {

    public static final ActionId ID = WorldActionIds.SET_BORDER_WARNING;
    private static final int TICKS_PER_SECOND = 20;

    private final TaskScheduler scheduler;

    public SetWorldBorderWarningAction(TaskScheduler scheduler) {
        this.scheduler = scheduler;
    }

    @Override
    public ActionId id() {
        return ID;
    }

    @Override
    public CompletableFuture<ActionResult<Void>> execute(ActionContext context, SetWorldBorderWarningInput input) {
        CompletableFuture<ActionResult<Void>> future = new CompletableFuture<>();
        scheduler.runOnMainThread(() -> {
            World world = Bukkit.getWorld(input.worldName());
            if (world == null) {
                future.complete(WorldActionSupport.worldNotFound());
                return;
            }
            WorldBorder border = world.getWorldBorder();
            border.setWarningDistance(input.distance());
            border.setWarningTimeTicks(input.timeSeconds() * TICKS_PER_SECOND);
            future.complete(ActionResult.success(null));
        });
        return future;
    }
}

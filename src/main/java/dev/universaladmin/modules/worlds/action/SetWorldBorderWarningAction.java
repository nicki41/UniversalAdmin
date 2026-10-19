package dev.universaladmin.modules.worlds.action;

import dev.universaladmin.action.Action;
import dev.universaladmin.action.ActionContext;
import dev.universaladmin.action.ActionId;
import dev.universaladmin.action.ActionResult;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.modules.worlds.WorldBorderWarnings;
import dev.universaladmin.scheduler.TaskScheduler;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldBorder;

/** Sets warning distance and time - the time through {@link WorldBorderWarnings}, which works on every supported Paper version. */
public final class SetWorldBorderWarningAction implements Action<SetWorldBorderWarningInput, Void> {

    public static final ActionId ID = WorldActionIds.SET_BORDER_WARNING;

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
            WorldBorderWarnings.setSeconds(border, input.timeSeconds());
            future.complete(ActionResult.success(null, MessageKey.of("worlds.result.border-warning"),
                    world.getName(), Integer.toString(input.distance()), Integer.toString(input.timeSeconds())));
        });
        return future;
    }
}

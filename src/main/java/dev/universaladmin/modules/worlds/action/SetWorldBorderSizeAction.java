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

/**
 * {@code setSize(double, long seconds)} is flagged deprecated-for-removal on
 * the target Paper API build, in favour of a new {@code changeSize(double,
 * long)} - but that replacement's unit isn't documented anywhere yet (too
 * new for any published javadoc at time of writing), and guessing wrong
 * would silently make border transitions run 20x too fast or too slow. The
 * known-correct, still-functional seconds-based overload is kept
 * deliberately until Paper documents the replacement - see
 * docs/user/modules/worlds.md's "World Border" section.
 */
public final class SetWorldBorderSizeAction implements Action<SetWorldBorderSizeInput, Void> {

    public static final ActionId ID = WorldActionIds.SET_BORDER_SIZE;

    private final TaskScheduler scheduler;

    public SetWorldBorderSizeAction(TaskScheduler scheduler) {
        this.scheduler = scheduler;
    }

    @Override
    public ActionId id() {
        return ID;
    }

    @Override
    @SuppressWarnings({"deprecation", "removal"})
    public CompletableFuture<ActionResult<Void>> execute(ActionContext context, SetWorldBorderSizeInput input) {
        CompletableFuture<ActionResult<Void>> future = new CompletableFuture<>();
        scheduler.runOnMainThread(() -> {
            World world = Bukkit.getWorld(input.worldName());
            if (world == null) {
                future.complete(WorldActionSupport.worldNotFound());
                return;
            }
            WorldBorder border = world.getWorldBorder();
            long transitionSeconds = input.transitionSeconds() != null ? input.transitionSeconds() : 0L;
            if (transitionSeconds > 0) {
                border.setSize(input.size(), transitionSeconds);
            } else {
                border.setSize(input.size());
            }
            future.complete(ActionResult.success(null));
        });
        return future;
    }
}

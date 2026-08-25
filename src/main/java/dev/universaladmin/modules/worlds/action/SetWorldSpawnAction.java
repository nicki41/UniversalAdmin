package dev.universaladmin.modules.worlds.action;

import dev.universaladmin.action.Action;
import dev.universaladmin.action.ActionContext;
import dev.universaladmin.action.ActionId;
import dev.universaladmin.action.ActionResult;
import dev.universaladmin.action.ActorType;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.scheduler.TaskScheduler;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

/** Sets {@code worldName}'s spawn point - to explicit coordinates, or the acting player's current location if none were given. */
public final class SetWorldSpawnAction implements Action<SetWorldSpawnInput, Void> {

    public static final ActionId ID = WorldActionIds.SET_SPAWN;

    private final TaskScheduler scheduler;

    public SetWorldSpawnAction(TaskScheduler scheduler) {
        this.scheduler = scheduler;
    }

    @Override
    public ActionId id() {
        return ID;
    }

    @Override
    public CompletableFuture<ActionResult<Void>> execute(ActionContext context, SetWorldSpawnInput input) {
        CompletableFuture<ActionResult<Void>> future = new CompletableFuture<>();
        scheduler.runOnMainThread(() -> {
            World world = Bukkit.getWorld(input.worldName());
            if (world == null) {
                future.complete(WorldActionSupport.worldNotFound());
                return;
            }
            Location location = resolveLocation(context, world, input);
            if (location == null) {
                future.complete(ActionResult.failure(ActionResult.FailureReason.NOT_FOUND, MessageKey.of("worlds.action.actor-offline")));
                return;
            }
            boolean applied = world.setSpawnLocation(location);
            future.complete(applied
                    ? ActionResult.success(null)
                    : ActionResult.failure(ActionResult.FailureReason.INTERNAL_ERROR, "Failed to set spawn location"));
        });
        return future;
    }

    private Location resolveLocation(ActionContext context, World world, SetWorldSpawnInput input) {
        if (input.x() != null && input.y() != null && input.z() != null) {
            float yaw = input.yaw() != null ? input.yaw() : 0f;
            return new Location(world, input.x(), input.y(), input.z(), yaw, 0f);
        }
        Player admin = context.actor().type() == ActorType.PLAYER && context.actor().playerId() != null
                ? Bukkit.getPlayer(context.actor().playerId())
                : null;
        return admin != null ? admin.getLocation() : null;
    }
}

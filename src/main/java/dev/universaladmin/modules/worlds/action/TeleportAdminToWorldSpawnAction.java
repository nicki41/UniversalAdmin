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
import org.bukkit.World;
import org.bukkit.entity.Player;

/** Teleports the acting player to {@code worldName}'s spawn. Raw {@code String} input - the world name. */
public final class TeleportAdminToWorldSpawnAction implements Action<String, Void> {

    public static final ActionId ID = WorldActionIds.TELEPORT_TO_SPAWN;

    private final TaskScheduler scheduler;

    public TeleportAdminToWorldSpawnAction(TaskScheduler scheduler) {
        this.scheduler = scheduler;
    }

    @Override
    public ActionId id() {
        return ID;
    }

    @Override
    public CompletableFuture<ActionResult<Void>> execute(ActionContext context, String worldName) {
        CompletableFuture<ActionResult<Void>> future = new CompletableFuture<>();
        scheduler.runOnMainThread(() -> {
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                future.complete(WorldActionSupport.worldNotFound());
                return;
            }
            Player admin = context.actor().type() == ActorType.PLAYER && context.actor().playerId() != null
                    ? Bukkit.getPlayer(context.actor().playerId())
                    : null;
            if (admin == null) {
                future.complete(ActionResult.failure(ActionResult.FailureReason.NOT_FOUND, MessageKey.of("worlds.action.actor-offline")));
                return;
            }
            admin.teleportAsync(world.getSpawnLocation()).thenAccept(success -> future.complete(Boolean.TRUE.equals(success)
                    ? ActionResult.success(null)
                    : ActionResult.failure(ActionResult.FailureReason.INTERNAL_ERROR, "Teleport did not complete")));
        });
        return future;
    }
}

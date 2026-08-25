package dev.universaladmin.modules.moderation.action;

import dev.universaladmin.action.Action;
import dev.universaladmin.action.ActionContext;
import dev.universaladmin.action.ActionId;
import dev.universaladmin.action.ActionResult;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.modules.moderation.VanishService;
import dev.universaladmin.scheduler.TaskScheduler;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/** Toggles the acting player's own vanish status - self-directed, no target beyond the actor. */
public final class VanishAction implements Action<Void, Boolean> {

    public static final ActionId ID = ModerationActionIds.VANISH;

    private final TaskScheduler scheduler;
    private final VanishService vanishService;

    public VanishAction(TaskScheduler scheduler, VanishService vanishService) {
        this.scheduler = scheduler;
        this.vanishService = vanishService;
    }

    @Override
    public ActionId id() {
        return ID;
    }

    @Override
    public CompletableFuture<ActionResult<Boolean>> execute(ActionContext context, Void input) {
        CompletableFuture<ActionResult<Boolean>> future = new CompletableFuture<>();
        scheduler.runOnMainThread(() -> {
            Player player = context.actor().playerId() != null ? Bukkit.getPlayer(context.actor().playerId()) : null;
            if (player == null) {
                future.complete(ActionResult.failure(ActionResult.FailureReason.NOT_FOUND, MessageKey.of("moderation.action.offline")));
                return;
            }
            vanishService.toggle(player).whenComplete((newState, error) -> {
                if (error != null) {
                    future.complete(ActionResult.failure(ActionResult.FailureReason.INTERNAL_ERROR, error.getMessage()));
                } else {
                    future.complete(ActionResult.success(newState));
                }
            });
        });
        return future;
    }
}

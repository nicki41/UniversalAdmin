package dev.universaladmin.modules.moderation.action;

import dev.universaladmin.action.Action;
import dev.universaladmin.action.ActionContext;
import dev.universaladmin.action.ActionId;
import dev.universaladmin.action.ActionResult;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.modules.moderation.GodmodeState;
import dev.universaladmin.scheduler.TaskScheduler;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Toggles the acting player's own godmode status via {@link
 * org.bukkit.entity.Entity#setInvulnerable(boolean)} - no {@code
 * EntityDamageEvent} listener anywhere, avoiding the cancellation side
 * effects ("no unnecessary side effects") that approach would carry.
 */
public final class GodmodeAction implements Action<Void, Boolean> {

    public static final ActionId ID = ModerationActionIds.GODMODE;

    private final TaskScheduler scheduler;
    private final GodmodeState state;

    public GodmodeAction(TaskScheduler scheduler, GodmodeState state) {
        this.scheduler = scheduler;
        this.state = state;
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
            boolean newState = !state.isEnabled(player.getUniqueId());
            state.setEnabled(player.getUniqueId(), newState);
            player.setInvulnerable(newState);
            future.complete(ActionResult.success(newState));
        });
        return future;
    }
}

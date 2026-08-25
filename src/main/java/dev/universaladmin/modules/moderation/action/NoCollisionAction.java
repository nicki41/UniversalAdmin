package dev.universaladmin.modules.moderation.action;

import dev.universaladmin.action.Action;
import dev.universaladmin.action.ActionContext;
import dev.universaladmin.action.ActionId;
import dev.universaladmin.action.ActionResult;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.modules.moderation.CollisionState;
import dev.universaladmin.modules.moderation.ModerationSettings;
import dev.universaladmin.modules.moderation.StaffModeState;
import dev.universaladmin.modules.moderation.VanishService;
import dev.universaladmin.scheduler.TaskScheduler;
import dev.universaladmin.settings.SettingsService;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/** Toggles the acting player's own manual no-collision flag - see {@link CollisionState#refresh}. */
public final class NoCollisionAction implements Action<Void, Boolean> {

    public static final ActionId ID = ModerationActionIds.NO_COLLISION;

    private final TaskScheduler scheduler;
    private final CollisionState collisionState;
    private final VanishService vanishService;
    private final StaffModeState staffModeState;
    private final SettingsService settings;

    public NoCollisionAction(
            TaskScheduler scheduler, CollisionState collisionState, VanishService vanishService,
            StaffModeState staffModeState, SettingsService settings) {
        this.scheduler = scheduler;
        this.collisionState = collisionState;
        this.vanishService = vanishService;
        this.staffModeState = staffModeState;
        this.settings = settings;
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
            boolean newState = !collisionState.isManuallyDisabled(player.getUniqueId());
            collisionState.setManuallyDisabled(player.getUniqueId(), newState);
            collisionState.refresh(player, vanishService.isVanished(player.getUniqueId()),
                    staffModeState.isActive(player.getUniqueId()) && settings.get(ModerationSettings.STAFFMODE_AUTO_NOCOLLISION));
            future.complete(ActionResult.success(newState));
        });
        return future;
    }
}

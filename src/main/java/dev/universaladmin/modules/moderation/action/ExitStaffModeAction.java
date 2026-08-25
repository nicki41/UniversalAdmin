package dev.universaladmin.modules.moderation.action;

import dev.universaladmin.action.Action;
import dev.universaladmin.action.ActionContext;
import dev.universaladmin.action.ActionId;
import dev.universaladmin.action.ActionResult;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.modules.moderation.StaffModeService;
import dev.universaladmin.scheduler.TaskScheduler;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class ExitStaffModeAction implements Action<Void, Void> {

    public static final ActionId ID = ModerationActionIds.STAFF_MODE_EXIT;

    private final TaskScheduler scheduler;
    private final StaffModeService staffModeService;

    public ExitStaffModeAction(TaskScheduler scheduler, StaffModeService staffModeService) {
        this.scheduler = scheduler;
        this.staffModeService = staffModeService;
    }

    @Override
    public ActionId id() {
        return ID;
    }

    @Override
    public CompletableFuture<ActionResult<Void>> execute(ActionContext context, Void input) {
        CompletableFuture<ActionResult<Void>> future = new CompletableFuture<>();
        scheduler.runOnMainThread(() -> {
            Player player = context.actor().playerId() != null ? Bukkit.getPlayer(context.actor().playerId()) : null;
            if (player == null) {
                future.complete(ActionResult.failure(ActionResult.FailureReason.NOT_FOUND, MessageKey.of("moderation.action.offline")));
                return;
            }
            staffModeService.exit(player).whenComplete((restored, error) -> {
                if (error != null) {
                    future.complete(ActionResult.failure(ActionResult.FailureReason.INTERNAL_ERROR, error.getMessage()));
                } else if (restored) {
                    future.complete(ActionResult.success(null));
                } else {
                    future.complete(ActionResult.failure(
                            ActionResult.FailureReason.NOT_FOUND, MessageKey.of("moderation.action.staffmode-not-active")));
                }
            });
        });
        return future;
    }
}

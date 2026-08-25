package dev.universaladmin.modules.moderation.action;

import dev.universaladmin.action.Action;
import dev.universaladmin.action.ActionContext;
import dev.universaladmin.action.ActionId;
import dev.universaladmin.action.ActionResult;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.modules.moderation.StaffModeService;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Manually recovers a pending staff-mode snapshot for {@code targetId}
 * (self or another online player) - the {@code /admin staff recover}
 * command's backing action, and reusable from a future GUI entry point.
 * Input is a bare {@link UUID}: no other data is needed, so no wrapper
 * record.
 */
public final class RecoverStaffSnapshotAction implements Action<UUID, StaffModeService.RecoveryOutcome> {

    public static final ActionId ID = ModerationActionIds.STAFF_MODE_RECOVER;

    private final StaffModeService staffModeService;

    public RecoverStaffSnapshotAction(StaffModeService staffModeService) {
        this.staffModeService = staffModeService;
    }

    @Override
    public ActionId id() {
        return ID;
    }

    @Override
    public CompletableFuture<ActionResult<StaffModeService.RecoveryOutcome>> execute(ActionContext context, UUID targetId) {
        return staffModeService.recover(targetId).thenApply(outcome -> switch (outcome) {
            case RECOVERED -> ActionResult.success(outcome);
            case NOT_ONLINE -> ActionResult.failure(ActionResult.FailureReason.NOT_FOUND, MessageKey.of("moderation.action.offline"));
            case NO_SNAPSHOT -> ActionResult.failure(ActionResult.FailureReason.NOT_FOUND, MessageKey.of("moderation.action.staffmode-no-snapshot"));
        });
    }
}

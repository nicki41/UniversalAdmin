package dev.universaladmin.modules.moderation.action;

import dev.universaladmin.action.Action;
import dev.universaladmin.action.ActionContext;
import dev.universaladmin.action.ActionId;
import dev.universaladmin.action.ActionResult;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.modules.moderation.FreezeRuntimeState;
import dev.universaladmin.modules.moderation.Punishment;
import dev.universaladmin.modules.moderation.PunishmentService;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Revokes the currently-active FREEZE for the target - independent action against the repository, not a {@code ReversibleAction} undo (see {@link UnbanAction}). */
public final class UnfreezeAction implements Action<UnfreezeInput, List<Punishment>> {

    public static final ActionId ID = ModerationActionIds.UNFREEZE;

    private final PunishmentService punishmentService;
    private final FreezeRuntimeState freezeState;

    public UnfreezeAction(PunishmentService punishmentService, FreezeRuntimeState freezeState) {
        this.punishmentService = punishmentService;
        this.freezeState = freezeState;
    }

    @Override
    public ActionId id() {
        return ID;
    }

    @Override
    public CompletableFuture<ActionResult<List<Punishment>>> execute(ActionContext context, UnfreezeInput input) {
        return punishmentService.unfreeze(input.targetId(), context.actor().displayName()).thenApply(revoked -> {
            if (revoked.isEmpty()) {
                return ActionResult.failure(ActionResult.FailureReason.NOT_FOUND, MessageKey.of("moderation.action.not-frozen"));
            }
            freezeState.setFrozen(input.targetId(), false);
            return ActionResult.success(revoked);
        });
    }
}

package dev.universaladmin.modules.moderation.action;

import dev.universaladmin.action.Action;
import dev.universaladmin.action.ActionContext;
import dev.universaladmin.action.ActionId;
import dev.universaladmin.action.ActionResult;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.modules.moderation.Punishment;
import dev.universaladmin.modules.moderation.PunishmentService;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Revokes every currently-active MUTE/TEMP_MUTE for the target - independent action against the repository, not a {@code ReversibleAction} undo (see {@link UnbanAction}). */
public final class UnmuteAction implements Action<UnmuteInput, List<Punishment>> {

    public static final ActionId ID = ModerationActionIds.UNMUTE;

    private final PunishmentService punishmentService;

    public UnmuteAction(PunishmentService punishmentService) {
        this.punishmentService = punishmentService;
    }

    @Override
    public ActionId id() {
        return ID;
    }

    @Override
    public CompletableFuture<ActionResult<List<Punishment>>> execute(ActionContext context, UnmuteInput input) {
        return punishmentService.unmute(input.targetId(), context.actor().displayName()).thenApply(revoked -> revoked.isEmpty()
                ? ActionResult.failure(ActionResult.FailureReason.NOT_FOUND, MessageKey.of("moderation.action.not-muted"))
                : ActionResult.success(revoked));
    }
}

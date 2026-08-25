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

/** Revokes every currently-active BAN/TEMP_BAN/IP_BAN for the target - not a {@link dev.universaladmin.action.ReversibleAction} undo of BAN, an independent action against the repository (no undo-history stack exists, see docs/architecture/actions.md). */
public final class UnbanAction implements Action<UnbanInput, List<Punishment>> {

    public static final ActionId ID = ModerationActionIds.UNBAN;

    private final PunishmentService punishmentService;

    public UnbanAction(PunishmentService punishmentService) {
        this.punishmentService = punishmentService;
    }

    @Override
    public ActionId id() {
        return ID;
    }

    @Override
    public CompletableFuture<ActionResult<List<Punishment>>> execute(ActionContext context, UnbanInput input) {
        return punishmentService.unban(input.targetId(), context.actor().displayName()).thenApply(revoked -> revoked.isEmpty()
                ? ActionResult.failure(ActionResult.FailureReason.NOT_FOUND, MessageKey.of("moderation.action.not-banned"))
                : ActionResult.success(revoked));
    }
}

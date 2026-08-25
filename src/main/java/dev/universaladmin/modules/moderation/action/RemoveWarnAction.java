package dev.universaladmin.modules.moderation.action;

import dev.universaladmin.action.Action;
import dev.universaladmin.action.ActionContext;
import dev.universaladmin.action.ActionId;
import dev.universaladmin.action.ActionResult;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.modules.moderation.Punishment;
import dev.universaladmin.modules.moderation.PunishmentService;
import java.util.concurrent.CompletableFuture;

/** Removes exactly one warning by id - independent action against the repository, not a {@code ReversibleAction} undo (see {@link UnbanAction}). */
public final class RemoveWarnAction implements Action<RemoveWarnInput, Punishment> {

    public static final ActionId ID = ModerationActionIds.REMOVE_WARN;

    private final PunishmentService punishmentService;

    public RemoveWarnAction(PunishmentService punishmentService) {
        this.punishmentService = punishmentService;
    }

    @Override
    public ActionId id() {
        return ID;
    }

    @Override
    public CompletableFuture<ActionResult<Punishment>> execute(ActionContext context, RemoveWarnInput input) {
        return punishmentService.removeWarn(input.warnId(), context.actor().displayName()).thenApply(revoked -> revoked
                .<ActionResult<Punishment>>map(ActionResult::success)
                .orElseGet(() -> ActionResult.failure(ActionResult.FailureReason.NOT_FOUND, MessageKey.of("moderation.action.warn-not-found"))));
    }
}

package dev.universaladmin.modules.moderation.action;

import dev.universaladmin.action.Action;
import dev.universaladmin.action.ActionContext;
import dev.universaladmin.action.ActionId;
import dev.universaladmin.action.ActionResult;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.modules.moderation.ModerationPolicy;
import dev.universaladmin.modules.moderation.Punishment;
import dev.universaladmin.modules.moderation.PunishmentEnforcer;
import dev.universaladmin.modules.moderation.PunishmentService;
import dev.universaladmin.modules.moderation.PunishmentType;
import dev.universaladmin.modules.moderation.TargetNames;
import dev.universaladmin.scheduler.TaskScheduler;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;

/**
 * Backs both {@code MUTE} and {@code TEMP_MUTE} - same shared-class-two-
 * registrations shape as {@link BanAction}. Enforcement itself happens at
 * chat time ({@code ModerationChatListener}, against the database every
 * server shares), not here - this only persists the record and tells the
 * target, if they are online here ({@link PunishmentEnforcer}).
 */
public final class MuteAction implements Action<MuteInput, Punishment> {

    private final ActionId id;
    private final TaskScheduler scheduler;
    private final PunishmentService punishmentService;
    private final PunishmentEnforcer enforcer;
    private final ModerationPolicy policy;
    private final TargetNames names;

    public MuteAction(
            ActionId id, TaskScheduler scheduler, PunishmentService punishmentService, PunishmentEnforcer enforcer,
            ModerationPolicy policy, TargetNames names) {
        this.id = id;
        this.scheduler = scheduler;
        this.punishmentService = punishmentService;
        this.enforcer = enforcer;
        this.policy = policy;
        this.names = names;
    }

    @Override
    public ActionId id() {
        return id;
    }

    @Override
    public CompletableFuture<ActionResult<Punishment>> execute(ActionContext context, MuteInput input) {
        boolean temporary = id.equals(ModerationActionIds.TEMP_MUTE);
        Instant expiresAt = temporary ? input.expiresAt() : null;
        PunishmentType type = temporary ? PunishmentType.TEMP_MUTE : PunishmentType.MUTE;

        if (!policy.canPunish(context.actor(), type, input.targetId())) {
            return CompletableFuture.completedFuture(ActionResult.failure(
                    ActionResult.FailureReason.NOT_PERMITTED, MessageKey.of("moderation.action.policy-denied")));
        }

        CompletableFuture<ActionResult<Punishment>> future = new CompletableFuture<>();
        scheduler.runOnMainThread(() -> {
            names.nameOf(input.targetId()).thenCompose(targetName -> punishmentService.issue(type, input.targetId(), targetName, null,
                            context.actor().playerId(), context.actor().displayName(), input.reason(), expiresAt, input.template()))
                    .whenComplete((punishment, error) -> {
                        if (error != null) {
                            future.complete(ActionResult.failure(ActionResult.FailureReason.INTERNAL_ERROR, error.getMessage()));
                            return;
                        }
                        scheduler.runOnMainThread(() -> enforcer.enforce(punishment));
                        future.complete(ModerationResults.issued(punishment));
                    });
        });
        return future;
    }
}

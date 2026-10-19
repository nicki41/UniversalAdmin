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
 * Backs both {@code BAN} and {@code TEMP_BAN} - one instance is registered
 * under each {@link ActionId} (see {@link ModerationActionIds}), each with
 * its own permission. {@link #id} decides which {@link PunishmentType} is
 * actually persisted (and, for {@code BAN}, that any {@code expiresAt} on
 * the input is ignored - permanent means permanent regardless of what was
 * passed in), so misuse of the shared {@link BanInput} can't silently ban
 * someone permanently through the temp-ban permission or vice versa.
 * Removes the target right away if they are online here
 * ({@link PunishmentEnforcer}); on another server of the network the
 * {@code NetworkModerationBridge} does the same there.
 */
public final class BanAction implements Action<BanInput, Punishment> {

    private final ActionId id;
    private final TaskScheduler scheduler;
    private final PunishmentService punishmentService;
    private final PunishmentEnforcer enforcer;
    private final ModerationPolicy policy;
    private final TargetNames names;

    public BanAction(
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
    public CompletableFuture<ActionResult<Punishment>> execute(ActionContext context, BanInput input) {
        boolean temporary = id.equals(ModerationActionIds.TEMP_BAN);
        Instant expiresAt = temporary ? input.expiresAt() : null;
        PunishmentType type = temporary ? PunishmentType.TEMP_BAN : PunishmentType.BAN;

        if (!policy.canPunish(context.actor(), type, input.targetId())) {
            return CompletableFuture.completedFuture(ActionResult.failure(
                    ActionResult.FailureReason.NOT_PERMITTED, MessageKey.of("moderation.action.policy-denied")));
        }

        CompletableFuture<ActionResult<Punishment>> future = new CompletableFuture<>();
        scheduler.runOnMainThread(() -> {
            names.nameOf(input.targetId()).thenCompose(targetName -> punishmentService.issue(type, input.targetId(), targetName, null,
                            context.actor().playerId(), context.actor().displayName(), input.reason(), expiresAt))
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

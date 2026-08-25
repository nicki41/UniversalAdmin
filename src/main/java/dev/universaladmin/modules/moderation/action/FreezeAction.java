package dev.universaladmin.modules.moderation.action;

import dev.universaladmin.action.Action;
import dev.universaladmin.action.ActionContext;
import dev.universaladmin.action.ActionId;
import dev.universaladmin.action.ActionResult;
import dev.universaladmin.localization.ComponentMessages;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.localization.MessageService;
import dev.universaladmin.modules.moderation.FreezeRuntimeState;
import dev.universaladmin.modules.moderation.ModerationPolicy;
import dev.universaladmin.modules.moderation.Punishment;
import dev.universaladmin.modules.moderation.PunishmentService;
import dev.universaladmin.modules.moderation.PunishmentType;
import dev.universaladmin.scheduler.TaskScheduler;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Freezes a target - permanent (no expiry), revoked via {@link UnfreezeAction}.
 * Updates {@link FreezeRuntimeState} so the enforcement listeners pick it up
 * immediately, and - like {@code MuteAction} - sends the target an
 * immediate notice if they're online. Unlike mute, {@code FreezeGuardListener}
 * does not repeat this on every blocked action: several of the events it
 * blocks (movement in particular) fire far more often than a chat message,
 * so a per-attempt reminder there would spam the frozen player instead of
 * informing them - one clear notice at freeze time is the right frequency.
 */
public final class FreezeAction implements Action<FreezeInput, Punishment> {

    public static final ActionId ID = ModerationActionIds.FREEZE;

    private final TaskScheduler scheduler;
    private final PunishmentService punishmentService;
    private final FreezeRuntimeState freezeState;
    private final ModerationPolicy policy;
    private final MessageService messages;

    public FreezeAction(
            TaskScheduler scheduler, PunishmentService punishmentService, FreezeRuntimeState freezeState,
            ModerationPolicy policy, MessageService messages) {
        this.scheduler = scheduler;
        this.punishmentService = punishmentService;
        this.freezeState = freezeState;
        this.policy = policy;
        this.messages = messages;
    }

    @Override
    public ActionId id() {
        return ID;
    }

    @Override
    public CompletableFuture<ActionResult<Punishment>> execute(ActionContext context, FreezeInput input) {
        if (!policy.canPunish(context.actor(), PunishmentType.FREEZE, input.targetId())) {
            return CompletableFuture.completedFuture(ActionResult.failure(
                    ActionResult.FailureReason.NOT_PERMITTED, MessageKey.of("moderation.action.policy-denied")));
        }
        CompletableFuture<ActionResult<Punishment>> future = new CompletableFuture<>();
        scheduler.runOnMainThread(() -> {
            Player online = Bukkit.getPlayer(input.targetId());
            String targetName = online != null ? online.getName() : input.targetId().toString();

            punishmentService.issue(PunishmentType.FREEZE, input.targetId(), targetName, null,
                            context.actor().playerId(), context.actor().displayName(), input.reason(), null)
                    .whenComplete((punishment, error) -> {
                        if (error != null) {
                            future.complete(ActionResult.failure(ActionResult.FailureReason.INTERNAL_ERROR, error.getMessage()));
                            return;
                        }
                        freezeState.setFrozen(input.targetId(), true);
                        if (online != null) {
                            scheduler.runOnMainThread(() -> online.sendMessage(frozenMessage(punishment)));
                        }
                        future.complete(ActionResult.success(punishment));
                    });
        });
        return future;
    }

    private Component frozenMessage(Punishment punishment) {
        String reason = punishment.reason() == null || punishment.reason().isBlank()
                ? messages.get(MessageKey.of("moderation.enforcement.no-reason"))
                : punishment.reason();
        return ComponentMessages.render(messages.get(MessageKey.of("moderation.enforcement.frozen"), reason));
    }
}

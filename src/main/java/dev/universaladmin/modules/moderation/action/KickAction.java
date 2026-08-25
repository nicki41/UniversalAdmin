package dev.universaladmin.modules.moderation.action;

import dev.universaladmin.action.Action;
import dev.universaladmin.action.ActionContext;
import dev.universaladmin.action.ActionId;
import dev.universaladmin.action.ActionResult;
import dev.universaladmin.localization.ComponentMessages;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.localization.MessageService;
import dev.universaladmin.modules.moderation.ModerationPolicy;
import dev.universaladmin.modules.moderation.Punishment;
import dev.universaladmin.modules.moderation.PunishmentService;
import dev.universaladmin.modules.moderation.PunishmentType;
import dev.universaladmin.scheduler.TaskScheduler;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/** Kicks an online target and records the kick for history - see {@link Punishment#kick}. */
public final class KickAction implements Action<KickInput, Punishment> {

    public static final ActionId ID = ModerationActionIds.KICK;

    private final TaskScheduler scheduler;
    private final PunishmentService punishmentService;
    private final MessageService messages;
    private final ModerationPolicy policy;

    public KickAction(TaskScheduler scheduler, PunishmentService punishmentService, MessageService messages, ModerationPolicy policy) {
        this.scheduler = scheduler;
        this.punishmentService = punishmentService;
        this.messages = messages;
        this.policy = policy;
    }

    @Override
    public ActionId id() {
        return ID;
    }

    @Override
    public CompletableFuture<ActionResult<Punishment>> execute(ActionContext context, KickInput input) {
        if (!policy.canPunish(context.actor(), PunishmentType.KICK, input.targetId())) {
            return CompletableFuture.completedFuture(ActionResult.failure(
                    ActionResult.FailureReason.NOT_PERMITTED, MessageKey.of("moderation.action.policy-denied")));
        }
        CompletableFuture<ActionResult<Punishment>> future = new CompletableFuture<>();
        scheduler.runOnMainThread(() -> {
            Player target = Bukkit.getPlayer(input.targetId());
            if (target == null) {
                future.complete(ActionResult.failure(
                        ActionResult.FailureReason.NOT_FOUND, MessageKey.of("moderation.action.offline")));
                return;
            }
            String targetName = target.getName();
            Component kickMessage = ComponentMessages.render(
                    messages.get(MessageKey.of("moderation.enforcement.kicked"), input.reason()));
            target.kick(kickMessage);

            punishmentService.recordKick(
                            input.targetId(), targetName, context.actor().playerId(), context.actor().displayName(), input.reason())
                    .whenComplete((punishment, error) -> {
                        if (error != null) {
                            future.complete(ActionResult.failure(ActionResult.FailureReason.INTERNAL_ERROR, error.getMessage()));
                        } else {
                            future.complete(ActionResult.success(punishment));
                        }
                    });
        });
        return future;
    }
}

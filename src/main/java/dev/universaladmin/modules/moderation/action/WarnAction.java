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
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/** Records a warning - persistent, permanent (no expiry), removable one at a time via {@link RemoveWarnAction}. */
public final class WarnAction implements Action<WarnInput, Punishment> {

    public static final ActionId ID = ModerationActionIds.WARN;

    private final TaskScheduler scheduler;
    private final PunishmentService punishmentService;
    private final MessageService messages;
    private final ModerationPolicy policy;

    public WarnAction(TaskScheduler scheduler, PunishmentService punishmentService, MessageService messages, ModerationPolicy policy) {
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
    public CompletableFuture<ActionResult<Punishment>> execute(ActionContext context, WarnInput input) {
        if (!policy.canPunish(context.actor(), PunishmentType.WARN, input.targetId())) {
            return CompletableFuture.completedFuture(ActionResult.failure(
                    ActionResult.FailureReason.NOT_PERMITTED, MessageKey.of("moderation.action.policy-denied")));
        }
        CompletableFuture<ActionResult<Punishment>> future = new CompletableFuture<>();
        scheduler.runOnMainThread(() -> {
            Player online = Bukkit.getPlayer(input.targetId());
            String targetName = online != null ? online.getName() : input.targetId().toString();

            punishmentService.issue(PunishmentType.WARN, input.targetId(), targetName, null,
                            context.actor().playerId(), context.actor().displayName(), input.reason(), null)
                    .whenComplete((punishment, error) -> {
                        if (error != null) {
                            future.complete(ActionResult.failure(ActionResult.FailureReason.INTERNAL_ERROR, error.getMessage()));
                            return;
                        }
                        if (online != null) {
                            scheduler.runOnMainThread(() -> online.sendMessage(ComponentMessages.render(
                                    messages.get(MessageKey.of("moderation.enforcement.warned"), input.reason()))));
                        }
                        future.complete(ActionResult.success(punishment));
                    });
        });
        return future;
    }
}

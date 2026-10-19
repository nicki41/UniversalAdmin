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
import dev.universaladmin.network.NetworkPlayer;
import dev.universaladmin.network.NetworkService;
import dev.universaladmin.scheduler.TaskScheduler;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Kicks a player and records the kick for history - see {@link Punishment#kick}.
 * The player must be online: here, or (network mode) on another server, which
 * then does the kick itself when it hears about it ({@code NetworkModerationBridge}).
 */
public final class KickAction implements Action<KickInput, Punishment> {

    public static final ActionId ID = ModerationActionIds.KICK;

    private final TaskScheduler scheduler;
    private final PunishmentService punishmentService;
    private final PunishmentEnforcer enforcer;
    private final ModerationPolicy policy;
    private final NetworkService network;

    public KickAction(
            TaskScheduler scheduler, PunishmentService punishmentService, PunishmentEnforcer enforcer, ModerationPolicy policy,
            NetworkService network) {
        this.scheduler = scheduler;
        this.punishmentService = punishmentService;
        this.enforcer = enforcer;
        this.policy = policy;
        this.network = network;
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
            Player local = Bukkit.getPlayer(input.targetId());
            Optional<NetworkPlayer> elsewhere = local == null ? network.player(input.targetId()) : Optional.empty();
            if (local == null && elsewhere.isEmpty()) {
                future.complete(ActionResult.failure(
                        ActionResult.FailureReason.NOT_FOUND, MessageKey.of("moderation.action.offline")));
                return;
            }
            String targetName = local != null ? local.getName() : elsewhere.get().name();
            punishmentService.recordKick(
                            input.targetId(), targetName, context.actor().playerId(), context.actor().displayName(), input.reason())
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

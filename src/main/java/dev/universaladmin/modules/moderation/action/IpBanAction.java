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
import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Bans the target's current IP address - requires them to be online to
 * capture it (same {@code Player#getAddress()} idiom {@code
 * GetPlayerIpAddressAction} uses), since there is no other reliable source
 * for a player's IP in this codebase. Always kicks the target: an IP ban
 * that doesn't also end the current session would be surprising.
 */
public final class IpBanAction implements Action<IpBanInput, Punishment> {

    public static final ActionId ID = ModerationActionIds.IP_BAN;

    private final TaskScheduler scheduler;
    private final PunishmentService punishmentService;
    private final PunishmentEnforcer enforcer;
    private final ModerationPolicy policy;
    private final NetworkService network;

    public IpBanAction(
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
    public CompletableFuture<ActionResult<Punishment>> execute(ActionContext context, IpBanInput input) {
        if (!policy.canPunish(context.actor(), PunishmentType.IP_BAN, input.targetId())) {
            return CompletableFuture.completedFuture(ActionResult.failure(
                    ActionResult.FailureReason.NOT_PERMITTED, MessageKey.of("moderation.action.policy-denied")));
        }
        CompletableFuture<ActionResult<Punishment>> future = new CompletableFuture<>();
        scheduler.runOnMainThread(() -> {
            Player target = Bukkit.getPlayer(input.targetId());
            InetSocketAddress address = target != null ? target.getAddress() : null;
            if (target == null || address == null || address.getAddress() == null) {
                // The address is only known while the player is connected
                // here - on another server of the network, say where.
                Optional<NetworkPlayer> elsewhere = network.player(input.targetId());
                future.complete(elsewhere.isPresent()
                        ? ActionResult.failure(ActionResult.FailureReason.CONFLICT, MessageKey.of("moderation.action.ipban-other-server"),
                                elsewhere.get().name(), elsewhere.get().server())
                        : ActionResult.failure(ActionResult.FailureReason.NOT_FOUND, MessageKey.of("moderation.action.no-ip-address")));
                return;
            }
            String ip = address.getAddress().getHostAddress();
            String targetName = target.getName();

            punishmentService.issue(PunishmentType.IP_BAN, input.targetId(), targetName, ip,
                            context.actor().playerId(), context.actor().displayName(), input.reason(), input.expiresAt())
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

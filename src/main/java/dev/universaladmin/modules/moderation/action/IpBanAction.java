package dev.universaladmin.modules.moderation.action;

import dev.universaladmin.action.Action;
import dev.universaladmin.action.ActionContext;
import dev.universaladmin.action.ActionId;
import dev.universaladmin.action.ActionResult;
import dev.universaladmin.localization.ComponentMessages;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.localization.MessageService;
import dev.universaladmin.modules.moderation.ModerationFormat;
import dev.universaladmin.modules.moderation.ModerationPolicy;
import dev.universaladmin.modules.moderation.Punishment;
import dev.universaladmin.modules.moderation.PunishmentService;
import dev.universaladmin.modules.moderation.PunishmentType;
import dev.universaladmin.scheduler.TaskScheduler;
import dev.universaladmin.settings.SettingsService;
import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.Component;
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
    private final MessageService messages;
    private final ModerationPolicy policy;
    private final SettingsService settings;

    public IpBanAction(
            TaskScheduler scheduler, PunishmentService punishmentService, MessageService messages,
            ModerationPolicy policy, SettingsService settings) {
        this.scheduler = scheduler;
        this.punishmentService = punishmentService;
        this.messages = messages;
        this.policy = policy;
        this.settings = settings;
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
                future.complete(ActionResult.failure(
                        ActionResult.FailureReason.NOT_FOUND, MessageKey.of("moderation.action.no-ip-address")));
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
                        scheduler.runOnMainThread(() -> target.kick(banMessage(punishment)));
                        future.complete(ActionResult.success(punishment));
                    });
        });
        return future;
    }

    private Component banMessage(Punishment punishment) {
        String key = punishment.permanent() ? "moderation.enforcement.banned-permanent" : "moderation.enforcement.banned-temp";
        return punishment.permanent()
                ? ComponentMessages.render(messages.get(MessageKey.of(key), punishment.reason()))
                : ComponentMessages.render(messages.get(MessageKey.of(key), punishment.reason(), ModerationFormat.expiry(punishment.expiresAt(), settings, messages)));
    }
}

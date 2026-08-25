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
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Backs both {@code BAN} and {@code TEMP_BAN} - one instance is registered
 * under each {@link ActionId} (see {@link ModerationActionIds}), each with
 * its own permission. {@link #id} decides which {@link PunishmentType} is
 * actually persisted (and, for {@code BAN}, that any {@code expiresAt} on
 * the input is ignored - permanent means permanent regardless of what was
 * passed in), so misuse of the shared {@link BanInput} can't silently ban
 * someone permanently through the temp-ban permission or vice versa.
 * Immediately kicks the target if they're currently online - a ban with no
 * live effect on an online player would be surprising.
 */
public final class BanAction implements Action<BanInput, Punishment> {

    private final ActionId id;
    private final TaskScheduler scheduler;
    private final PunishmentService punishmentService;
    private final MessageService messages;
    private final ModerationPolicy policy;
    private final SettingsService settings;

    public BanAction(
            ActionId id, TaskScheduler scheduler, PunishmentService punishmentService, MessageService messages,
            ModerationPolicy policy, SettingsService settings) {
        this.id = id;
        this.scheduler = scheduler;
        this.punishmentService = punishmentService;
        this.messages = messages;
        this.policy = policy;
        this.settings = settings;
    }

    @Override
    public ActionId id() {
        return id;
    }

    @Override
    public CompletableFuture<ActionResult<Punishment>> execute(ActionContext context, BanInput input) {
        boolean temporary = id.equals(ModerationActionIds.TEMP_BAN);
        java.time.Instant expiresAt = temporary ? input.expiresAt() : null;
        PunishmentType type = temporary ? PunishmentType.TEMP_BAN : PunishmentType.BAN;

        if (!policy.canPunish(context.actor(), type, input.targetId())) {
            return CompletableFuture.completedFuture(ActionResult.failure(
                    ActionResult.FailureReason.NOT_PERMITTED, MessageKey.of("moderation.action.policy-denied")));
        }

        CompletableFuture<ActionResult<Punishment>> future = new CompletableFuture<>();
        scheduler.runOnMainThread(() -> {
            Player online = Bukkit.getPlayer(input.targetId());
            String targetName = online != null ? online.getName() : input.targetId().toString();

            punishmentService.issue(type, input.targetId(), targetName, null,
                            context.actor().playerId(), context.actor().displayName(), input.reason(), expiresAt)
                    .whenComplete((punishment, error) -> {
                        if (error != null) {
                            future.complete(ActionResult.failure(ActionResult.FailureReason.INTERNAL_ERROR, error.getMessage()));
                            return;
                        }
                        if (online != null) {
                            scheduler.runOnMainThread(() -> online.kick(banMessage(punishment)));
                        }
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

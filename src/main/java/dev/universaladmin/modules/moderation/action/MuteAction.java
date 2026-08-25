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
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Backs both {@code MUTE} and {@code TEMP_MUTE} - same shared-class-two-
 * registrations shape as {@link BanAction}. Enforcement itself happens at
 * chat time ({@code ModerationChatListener}), not here - this only persists
 * the record and, if the target is online, sends them the mute notice.
 */
public final class MuteAction implements Action<MuteInput, Punishment> {

    private final ActionId id;
    private final TaskScheduler scheduler;
    private final PunishmentService punishmentService;
    private final MessageService messages;
    private final ModerationPolicy policy;
    private final SettingsService settings;

    public MuteAction(
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
                            scheduler.runOnMainThread(() -> online.sendMessage(muteMessage(punishment)));
                        }
                        future.complete(ActionResult.success(punishment));
                    });
        });
        return future;
    }

    private net.kyori.adventure.text.Component muteMessage(Punishment punishment) {
        String key = punishment.permanent() ? "moderation.enforcement.muted-permanent" : "moderation.enforcement.muted-temp";
        return punishment.permanent()
                ? ComponentMessages.render(messages.get(MessageKey.of(key), punishment.reason()))
                : ComponentMessages.render(messages.get(MessageKey.of(key), punishment.reason(), ModerationFormat.expiry(punishment.expiresAt(), settings, messages)));
    }
}

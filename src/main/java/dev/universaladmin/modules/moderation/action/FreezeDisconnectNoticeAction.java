package dev.universaladmin.modules.moderation.action;

import dev.universaladmin.action.Action;
import dev.universaladmin.action.ActionContext;
import dev.universaladmin.action.ActionId;
import dev.universaladmin.action.ActionResult;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.localization.MessageService;
import dev.universaladmin.modules.moderation.ModerationPermissions;
import dev.universaladmin.notification.Notification;
import dev.universaladmin.notification.NotificationService;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

/**
 * System-only: "a frozen player disconnected". Exists solely so this gets
 * an audit entry through the one sanctioned path ({@link
 * dev.universaladmin.action.ActionExecutor}'s automatic hook) instead of
 * {@code FreezeDisconnectListener} calling {@code AuditService} directly,
 * which docs/development/architecture-rules.md forbids - see {@code ModerationActionIds#FREEZE_DISCONNECT_NOTICE}.
 * The staff notification is a side effect of this action's own {@link
 * #execute}, the same way {@code BanAction} sends a kick message as a side
 * effect of its own.
 */
public final class FreezeDisconnectNoticeAction implements Action<UUID, Void> {

    public static final ActionId ID = ModerationActionIds.FREEZE_DISCONNECT_NOTICE;

    private final NotificationService notifications;
    private final MessageService messages;

    public FreezeDisconnectNoticeAction(NotificationService notifications, MessageService messages) {
        this.notifications = notifications;
        this.messages = messages;
    }

    @Override
    public ActionId id() {
        return ID;
    }

    @Override
    public CompletableFuture<ActionResult<Void>> execute(ActionContext context, UUID targetId) {
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetId);
        String targetName = target.getName() != null ? target.getName() : targetId.toString();
        String text = messages.get(MessageKey.of("moderation.enforcement.frozen-disconnect"), targetName);
        notifications.notifyStaff(ModerationPermissions.FREEZE, Notification.info(text));
        return CompletableFuture.completedFuture(ActionResult.success(null));
    }
}

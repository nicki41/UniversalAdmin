package dev.universaladmin.modules.moderation.gui;

import dev.universaladmin.action.ActionContext;
import dev.universaladmin.action.ActionId;
import dev.universaladmin.action.ActionResult;
import dev.universaladmin.action.Actor;
import dev.universaladmin.action.Source;
import dev.universaladmin.localization.ComponentMessages;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.localization.MessageService;
import dev.universaladmin.modules.moderation.ModerationFormat;
import dev.universaladmin.modules.moderation.Punishment;
import dev.universaladmin.modules.moderation.action.ModerationActionIds;
import dev.universaladmin.permission.bukkit.PermissiblePermissionEvaluator;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/**
 * Shared glue for every Moderation GUI page that calls into {@link
 * dev.universaladmin.action.ActionExecutor} - building the {@link
 * ActionContext}, rendering an {@link ActionResult} back to the viewer, and
 * the {@code execute -> notify -> reopen} sequence every page needs (see
 * {@code PlayerGuiActions} in the Players module for the identical pattern).
 */
final class ModerationGuiActions {

    private ModerationGuiActions() {
    }

    static ActionContext contextFor(Player viewer) {
        Actor actor = Actor.player(viewer.getUniqueId(), viewer.getName(), new PermissiblePermissionEvaluator(viewer));
        return new ActionContext(actor, Source.GUI);
    }

    /** Runs {@code id} with {@code input}, notifies {@code viewer} of the outcome, then runs {@code onDone} back on the main thread. */
    static <I> void runAction(ModerationGuiContext ctx, Player viewer, ActionId id, I input, Runnable onDone) {
        ctx.actionExecutor().<I, Object>execute(id, contextFor(viewer), input)
                .whenComplete((result, error) -> ctx.scheduler().runOnMainThread(() -> {
                    if (!viewer.isOnline()) {
                        return;
                    }
                    if (error != null) {
                        notifyError(viewer, ctx.messages());
                    } else {
                        notifyResult(viewer, ctx.messages(), id, result);
                    }
                    onDone.run();
                }));
    }

    static void notifyResult(Player viewer, MessageService messages, ActionId id, ActionResult<?> result) {
        Component message = switch (result) {
            case ActionResult.Success<?> success -> successText(messages, id, success.value());
            case ActionResult.Failure<?> failure -> ComponentMessages.render(failureText(messages, failure));
        };
        viewer.sendMessage(message);
    }

    static void notifyError(Player viewer, MessageService messages) {
        viewer.sendMessage(ComponentMessages.render(messages.get(MessageKey.of("moderation.gui.action.error"))));
    }

    /**
     * A specific, one-line "what actually happened" message per {@code id}
     * where one is mapped below, falling back to the generic "Done." for
     * anything unmapped (including every non-moderation {@link ActionId} a
     * moderation page might run, e.g. the Inventory/Ender Chest Inspectors'
     * {@code players.inventory.set}/{@code players.enderchest.set}).
     */
    private static Component successText(MessageService messages, ActionId id, Object value) {
        if (value instanceof Punishment punishment) {
            String key = punishmentSuccessKey(id);
            if (key != null) {
                return punishmentMessage(messages, key, punishment);
            }
        } else if (value instanceof List<?> revoked && !revoked.isEmpty() && revoked.get(0) instanceof Punishment first) {
            String key = revokeSuccessKey(id);
            if (key != null) {
                return ComponentMessages.render(messages.get(MessageKey.of(key), first.targetLastKnownName()));
            }
        } else if (value instanceof Boolean enabled) {
            String key = toggleSuccessKey(id);
            if (key != null) {
                String state = messages.get(MessageKey.of(enabled ? "moderation.gui.status.on" : "moderation.gui.status.off"));
                return ComponentMessages.render(messages.get(MessageKey.of(key), state));
            }
        } else {
            String key = plainSuccessKey(id);
            if (key != null) {
                return ComponentMessages.render(messages.get(MessageKey.of(key)));
            }
        }
        return ComponentMessages.render(messages.get(MessageKey.of("moderation.gui.action.success")));
    }

    /** @param key lang key taking (target, reason, duration) - {@code duration} is "" for a permanent punishment, ignored by every non-temp key. */
    private static Component punishmentMessage(MessageService messages, String key, Punishment punishment) {
        String reason = punishment.reason() == null || punishment.reason().isBlank()
                ? messages.get(MessageKey.of("common.none"))
                : punishment.reason();
        String duration = punishment.expiresAt() != null ? ModerationFormat.remaining(punishment.expiresAt(), messages) : "";
        return ComponentMessages.render(messages.get(MessageKey.of(key), punishment.targetLastKnownName(), reason, duration));
    }

    private static String punishmentSuccessKey(ActionId id) {
        if (id.equals(ModerationActionIds.KICK)) {
            return "moderation.gui.action.result.kick";
        }
        if (id.equals(ModerationActionIds.BAN)) {
            return "moderation.gui.action.result.ban";
        }
        if (id.equals(ModerationActionIds.TEMP_BAN)) {
            return "moderation.gui.action.result.tempban";
        }
        if (id.equals(ModerationActionIds.IP_BAN)) {
            return "moderation.gui.action.result.ipban";
        }
        if (id.equals(ModerationActionIds.MUTE)) {
            return "moderation.gui.action.result.mute";
        }
        if (id.equals(ModerationActionIds.TEMP_MUTE)) {
            return "moderation.gui.action.result.tempmute";
        }
        if (id.equals(ModerationActionIds.WARN)) {
            return "moderation.gui.action.result.warn";
        }
        if (id.equals(ModerationActionIds.FREEZE)) {
            return "moderation.gui.action.result.freeze";
        }
        if (id.equals(ModerationActionIds.REMOVE_WARN)) {
            return "moderation.gui.action.result.removewarn";
        }
        return null;
    }

    private static String revokeSuccessKey(ActionId id) {
        if (id.equals(ModerationActionIds.UNBAN)) {
            return "moderation.gui.action.result.unban";
        }
        if (id.equals(ModerationActionIds.UNMUTE)) {
            return "moderation.gui.action.result.unmute";
        }
        if (id.equals(ModerationActionIds.UNFREEZE)) {
            return "moderation.gui.action.result.unfreeze";
        }
        return null;
    }

    private static String toggleSuccessKey(ActionId id) {
        if (id.equals(ModerationActionIds.VANISH)) {
            return "moderation.gui.action.result.vanish";
        }
        if (id.equals(ModerationActionIds.GODMODE)) {
            return "moderation.gui.action.result.godmode";
        }
        if (id.equals(ModerationActionIds.NO_COLLISION)) {
            return "moderation.gui.action.result.collision";
        }
        return null;
    }

    private static String plainSuccessKey(ActionId id) {
        if (id.equals(ModerationActionIds.STAFF_MODE_ENTER)) {
            return "moderation.gui.action.result.staffmode-enter";
        }
        if (id.equals(ModerationActionIds.STAFF_MODE_EXIT)) {
            return "moderation.gui.action.result.staffmode-exit";
        }
        return null;
    }

    private static String failureText(MessageService messages, ActionResult.Failure<?> failure) {
        if (failure.messageKey() != null) {
            return messages.get(failure.messageKey(), failure.messageArgs().toArray());
        }
        return failure.message() != null ? failure.message() : failure.reason().name();
    }
}

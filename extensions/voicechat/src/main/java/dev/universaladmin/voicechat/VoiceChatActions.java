package dev.universaladmin.voicechat;

import de.maxhenkel.voicechat.api.Group;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import dev.universaladmin.action.Action;
import dev.universaladmin.action.ActionContext;
import dev.universaladmin.action.ActionId;
import dev.universaladmin.action.ActionResult;
import dev.universaladmin.core.PlayerRef;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.scheduler.TaskScheduler;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/** The voice chat's two actions - authorized and audited by UniversalAdmin like every other. */
final class VoiceChatActions {

    private VoiceChatActions() {
    }

    /** Puts the player into the staff voice group, or takes them out if they are in it. */
    static final class ToggleStaffGroup implements Action<PlayerRef, Boolean> {

        private final VoiceChatBridge bridge;
        private final StaffVoiceGroup staffGroup;
        private final TaskScheduler scheduler;

        ToggleStaffGroup(VoiceChatBridge bridge, StaffVoiceGroup staffGroup, TaskScheduler scheduler) {
            this.bridge = bridge;
            this.staffGroup = staffGroup;
            this.scheduler = scheduler;
        }

        @Override
        public ActionId id() {
            return VoiceChatModule.STAFF_GROUP;
        }

        @Override
        public CompletableFuture<ActionResult<Boolean>> execute(ActionContext context, PlayerRef player) {
            return scheduler.supplyOnMainThread(() -> {
                Optional<VoicechatConnection> connection = bridge.connection(player.id()).filter(VoicechatConnection::isConnected);
                if (connection.isEmpty()) {
                    return ActionResult.failure(ActionResult.FailureReason.VALIDATION,
                            MessageKey.of("voicechat.error.not-connected"), player.name());
                }
                if (staffGroup.contains(connection.get())) {
                    connection.get().setGroup(null);
                    return ActionResult.success(false, MessageKey.of("voicechat.result.left-staff-group"));
                }
                Optional<Group> group = staffGroup.group();
                if (group.isEmpty()) {
                    return ActionResult.failure(ActionResult.FailureReason.VALIDATION, MessageKey.of("voicechat.error.not-running"));
                }
                connection.get().setGroup(group.get());
                return ActionResult.success(true, MessageKey.of("voicechat.result.joined-staff-group"), group.get().getName());
            });
        }
    }

    /** Takes a player out of whatever voice group they are in. */
    static final class RemoveFromGroup implements Action<PlayerRef, String> {

        private final VoiceChatBridge bridge;
        private final TaskScheduler scheduler;

        RemoveFromGroup(VoiceChatBridge bridge, TaskScheduler scheduler) {
            this.bridge = bridge;
            this.scheduler = scheduler;
        }

        @Override
        public ActionId id() {
            return VoiceChatModule.REMOVE_FROM_GROUP;
        }

        @Override
        public CompletableFuture<ActionResult<String>> execute(ActionContext context, PlayerRef target) {
            return scheduler.supplyOnMainThread(() -> {
                Optional<VoicechatConnection> connection = bridge.connection(target.id());
                Group group = connection.map(VoicechatConnection::getGroup).orElse(null);
                if (group == null) {
                    return ActionResult.failure(ActionResult.FailureReason.NOT_FOUND,
                            MessageKey.of("voicechat.error.no-group"), target.name());
                }
                connection.get().setGroup(null);
                return ActionResult.success(group.getName(), MessageKey.of("voicechat.result.removed"), target.name(), group.getName());
            });
        }
    }
}

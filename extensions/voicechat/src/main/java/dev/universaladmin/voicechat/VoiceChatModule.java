package dev.universaladmin.voicechat;

import de.maxhenkel.voicechat.api.VoicechatConnection;
import dev.universaladmin.action.ActionDefinition;
import dev.universaladmin.action.ActionId;
import dev.universaladmin.action.ActionTarget;
import dev.universaladmin.command.BaseAdminCommand;
import dev.universaladmin.command.CommandInvocation;
import dev.universaladmin.core.PlayerRef;
import dev.universaladmin.core.UniversalAdmin;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.module.GuiIcon;
import dev.universaladmin.module.Module;
import dev.universaladmin.module.ModuleContext;
import dev.universaladmin.module.ModuleDescriptor;
import dev.universaladmin.module.ModuleId;
import dev.universaladmin.permission.PermissionDefault;
import dev.universaladmin.permission.PermissionDefinition;
import dev.universaladmin.permission.PermissionNode;
import dev.universaladmin.settings.SettingDefinition;
import dev.universaladmin.settings.SettingKey;
import dev.universaladmin.settings.SettingTypes;
import dev.universaladmin.settings.SettingsService;
import dev.universaladmin.status.PlayerStatus;
import dev.universaladmin.voice.VoiceChat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Everything UniversalAdmin Voice Chat adds, registered while the module is
 * enabled: the microphone gate (voice and full mutes, vanish), the
 * {@link VoiceChat} service that makes the core offer mute kinds, the staff
 * voice group, the profile entry, two audited actions and their settings and
 * permissions.
 */
final class VoiceChatModule implements Module {

    static final ModuleId ID = ModuleId.of("voicechat", "simplevoicechat");
    static final ActionId STAFF_GROUP = ActionId.of("voicechat", "staff-group");
    static final ActionId REMOVE_FROM_GROUP = ActionId.of("voicechat", "remove-from-group");
    static final PermissionNode STAFF_GROUP_PERMISSION = PermissionNode.of("universaladmin.voicechat.staffgroup");
    static final PermissionNode MANAGE = PermissionNode.of("universaladmin.voicechat.manage");
    static final SettingKey<Boolean> SILENCE_VANISHED = SettingKey.of("voicechat", "voicechat.silence-vanished");
    static final SettingKey<Boolean> NOTICES = SettingKey.of("voicechat", "voicechat.notices");
    static final SettingKey<String> STAFF_GROUP_NAME = SettingKey.of("voicechat", "voicechat.staff-group-name");

    private static final ModuleDescriptor DESCRIPTOR = ModuleDescriptor.builder(ID, "Voice Chat")
            .description("Simple Voice Chat: voice and full mutes, silent vanish, voice status on the profile, a staff voice group.")
            .icon(new GuiIcon("note_block", "Voice Chat"))
            .build();

    private final UniversalAdmin platform;
    private final VoiceChatBridge bridge;

    VoiceChatModule(UniversalAdmin platform, VoiceChatBridge bridge) {
        this.platform = platform;
        this.bridge = bridge;
    }

    @Override
    public ModuleDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public void onEnable(ModuleContext context) {
        SettingsService settings = platform.settings();
        context.registerSetting(SettingDefinition.builder(SILENCE_VANISHED, SettingTypes.BOOLEAN, true)
                .description("Vanished staff cannot be heard in the voice chat (except in the staff voice group).")
                .build());
        context.registerSetting(SettingDefinition.builder(NOTICES, SettingTypes.BOOLEAN, true)
                .description("Tell muted and vanished players in the action bar that nobody hears them.")
                .build());
        context.registerSetting(SettingDefinition.builder(STAFF_GROUP_NAME, SettingTypes.STRING, "Staff")
                .description("Name of the staff voice group - used when the group is created.")
                .build());
        context.registerPermission(new PermissionDefinition(STAFF_GROUP_PERMISSION,
                "Join and leave the staff voice group", PermissionDefault.OP));
        context.registerPermission(new PermissionDefinition(MANAGE,
                "See a player's voice chat status and take them out of a voice group", PermissionDefault.OP));

        StaffVoiceGroup staffGroup = new StaffVoiceGroup(bridge, () -> settings.get(STAFF_GROUP_NAME));
        String module = ID.key().name();
        context.registerAction(ActionDefinition.builder(new VoiceChatActions.ToggleStaffGroup(bridge, staffGroup, platform.scheduler()))
                .permission(STAFF_GROUP_PERMISSION)
                .module(module)
                .target(player -> Optional.of(ActionTarget.player(player.id(), player.name())))
                .auditSummary(player -> "Toggled the staff voice group for " + player.name())
                .build());
        context.registerAction(ActionDefinition.builder(new VoiceChatActions.RemoveFromGroup(bridge, platform.scheduler()))
                .permission(MANAGE)
                .module(module)
                .target(target -> Optional.of(ActionTarget.player(target.id(), target.name())))
                .auditSummary(target -> "Took " + target.name() + " out of their voice group")
                .build());
        context.registerCommand(new VoiceGroupCommand());
        context.registerPlayerMenuEntry(new VoiceProfileEntry(platform, bridge));
        context.registerService(VoiceChat.class, bridge);

        Optional<PlayerStatus> status = platform.services().get(PlayerStatus.class);
        if (status.isEmpty()) {
            context.logger().warning("The Moderation module is not enabled - mutes and vanish do not reach the voice chat.");
            return;
        }
        VoiceGate gate = new VoiceGate(status.get(), () -> settings.get(SILENCE_VANISHED));
        VoiceNotices notices = new VoiceNotices(platform.messages(), platform.scheduler());
        bridge.onMicrophone(event -> {
            VoicechatConnection sender = event.getSenderConnection();
            if (sender == null) {
                return;
            }
            UUID playerId = sender.getPlayer().getUuid();
            gate.check(playerId, staffGroup.contains(sender)).ifPresent(block -> {
                event.cancel();
                if (settings.get(NOTICES)) {
                    notices.show(playerId, block);
                }
            });
        });
        context.resources().closeable(() -> bridge.onMicrophone(event -> { }));
        context.registerListener(new Listener() {
            @EventHandler
            public void onQuit(PlayerQuitEvent event) {
                notices.forget(event.getPlayer().getUniqueId());
            }
        });
    }

    /** {@code /admin voicegroup} - also {@code /voicegroup} while shortcuts are on. */
    static final class VoiceGroupCommand extends BaseAdminCommand {

        VoiceGroupCommand() {
            super("voicegroup", STAFF_GROUP_PERMISSION, MessageKey.of("voicechat.command.voicegroup.usage"),
                    MessageKey.of("voicechat.command.voicegroup.description"));
            shortcuts("voicegroup");
        }

        @Override
        public void execute(CommandInvocation invocation) {
            invocation.player().ifPresentOrElse(
                    player -> invocation.run(STAFF_GROUP, new PlayerRef(player.getUniqueId(), player.getName())),
                    () -> invocation.reply("command.error.player-only"));
        }

        @Override
        public List<String> complete(CommandInvocation invocation) {
            return List.of();
        }
    }
}

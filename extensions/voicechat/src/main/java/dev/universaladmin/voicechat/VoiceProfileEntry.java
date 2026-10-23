package dev.universaladmin.voicechat;

import de.maxhenkel.voicechat.api.Group;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import dev.universaladmin.core.PlayerRef;
import dev.universaladmin.core.UniversalAdmin;
import dev.universaladmin.gui.GuiActions;
import dev.universaladmin.gui.GuiClickContext;
import dev.universaladmin.gui.GuiItem;
import dev.universaladmin.gui.PlayerMenuEntry;
import dev.universaladmin.localization.ComponentMessages;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.permission.PermissionNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * "Voice Chat" on the profile of a player on this server: whether the mod is
 * installed and connected and which group they are in; a click takes them
 * out of that group.
 */
final class VoiceProfileEntry implements PlayerMenuEntry {

    private final UniversalAdmin platform;
    private final VoiceChatBridge bridge;

    VoiceProfileEntry(UniversalAdmin platform, VoiceChatBridge bridge) {
        this.platform = platform;
        this.bridge = bridge;
    }

    @Override
    public String id() {
        return "voicechat:status";
    }

    @Override
    public int order() {
        return 40;
    }

    @Override
    public PermissionNode permission() {
        return VoiceChatModule.MANAGE;
    }

    @Override
    public boolean requiresOnline() {
        return true;
    }

    @Override
    public GuiItem icon(Player viewer, PlayerRef target) {
        Optional<VoicechatConnection> connection = bridge.connection(target.id());
        List<Component> lore = new ArrayList<>();
        if (connection.isEmpty() || !connection.get().isInstalled()) {
            lore.add(render("voicechat.gui.profile.not-installed"));
        } else if (connection.get().isDisabled()) {
            lore.add(render("voicechat.gui.profile.disabled"));
        } else {
            lore.add(render(connection.get().isConnected() ? "voicechat.gui.profile.connected" : "voicechat.gui.profile.disconnected"));
        }
        Group group = connection.map(VoicechatConnection::getGroup).orElse(null);
        if (group == null) {
            lore.add(render("voicechat.gui.profile.no-group"));
        } else {
            lore.add(render("voicechat.gui.profile.group", group.getName()));
            lore.add(render("voicechat.gui.profile.click-remove"));
        }
        return GuiItem.of(Material.NOTE_BLOCK, render("voicechat.gui.profile.title"), lore);
    }

    @Override
    public void onClick(GuiClickContext context, PlayerRef target) {
        Player viewer = context.viewer();
        GuiActions.runAndReport(platform.actionExecutor(), platform.scheduler(), platform.messages(), viewer,
                VoiceChatModule.REMOVE_FROM_GROUP, target, null, () -> { });
    }

    private Component render(String key, Object... args) {
        return ComponentMessages.render(platform.messages().get(MessageKey.of(key), args));
    }
}

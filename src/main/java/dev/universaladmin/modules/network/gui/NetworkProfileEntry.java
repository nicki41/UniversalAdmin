package dev.universaladmin.modules.network.gui;

import dev.universaladmin.core.PlayerRef;
import dev.universaladmin.gui.GuiActions;
import dev.universaladmin.gui.GuiClickContext;
import dev.universaladmin.gui.GuiItem;
import dev.universaladmin.gui.PlayerMenuEntry;
import dev.universaladmin.gui.PlayerMenuGroup;
import dev.universaladmin.localization.ComponentMessages;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.modules.network.NetworkPermissions;
import dev.universaladmin.modules.network.action.NetworkActions;
import dev.universaladmin.network.NetworkPlayer;
import dev.universaladmin.network.NetworkVisibility;
import dev.universaladmin.permission.PermissionNode;
import java.util.List;
import java.util.Optional;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * On the profile of a player who is on another server of the network: where
 * they are, and a click takes the staff member there, next to them.
 */
public final class NetworkProfileEntry implements PlayerMenuEntry {

    private final NetworkGuiContext ctx;

    public NetworkProfileEntry(NetworkGuiContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public String id() {
        return "core:network.location";
    }

    @Override
    public PlayerMenuGroup group() {
        return PlayerMenuGroup.HELP;
    }

    @Override
    public int order() {
        return 5;
    }

    @Override
    public PermissionNode permission() {
        return NetworkPermissions.GOTO;
    }

    @Override
    public boolean visible(Player viewer, PlayerRef target) {
        return elsewhere(viewer, target).isPresent();
    }

    @Override
    public GuiItem icon(Player viewer, PlayerRef target) {
        String server = elsewhere(viewer, target).map(NetworkPlayer::server).orElse("?");
        return GuiItem.of(Material.ENDER_EYE,
                ComponentMessages.render(ctx.messages().get(MessageKey.of("network.gui.profile.on-server"), server)),
                List.of(ComponentMessages.render(ctx.messages().get(MessageKey.of("network.gui.profile.click"), target.name()))));
    }

    @Override
    public void onClick(GuiClickContext context, PlayerRef target) {
        Player viewer = context.viewer();
        GuiActions.runAndReport(ctx.actionExecutor(), ctx.scheduler(), ctx.messages(), viewer,
                NetworkActions.GOTO, target.name(), null, viewer::closeInventory);
    }

    /** The player's network location, if they are online on another server - and {@code viewer} may see them there. */
    private Optional<NetworkPlayer> elsewhere(Player viewer, PlayerRef target) {
        return ctx.network().player(target.id())
                .filter(player -> !player.server().equalsIgnoreCase(ctx.network().serverName()))
                .filter(player -> NetworkVisibility.canSee(viewer, ctx.network(), player));
    }
}

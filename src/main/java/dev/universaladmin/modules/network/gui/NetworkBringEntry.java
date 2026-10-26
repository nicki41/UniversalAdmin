package dev.universaladmin.modules.network.gui;

import dev.universaladmin.core.PlayerRef;
import dev.universaladmin.gui.GuiActions;
import dev.universaladmin.gui.GuiClickContext;
import dev.universaladmin.gui.GuiItem;
import dev.universaladmin.gui.PlayerMenuEntry;
import dev.universaladmin.localization.ComponentMessages;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.modules.network.NetworkPermissions;
import dev.universaladmin.modules.network.action.NetworkActions;
import dev.universaladmin.network.NetworkVisibility;
import dev.universaladmin.permission.PermissionNode;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** On the profile of a player who is on another server of the network: bring them here, next to the staff member. */
public final class NetworkBringEntry implements PlayerMenuEntry {

    private final NetworkGuiContext ctx;

    public NetworkBringEntry(NetworkGuiContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public String id() {
        return "core:network.bring";
    }

    @Override
    public int order() {
        return 6;
    }

    @Override
    public PermissionNode permission() {
        return NetworkPermissions.SEND;
    }

    @Override
    public boolean visible(Player viewer, PlayerRef target) {
        return ctx.network().player(target.id())
                .filter(player -> !player.server().equalsIgnoreCase(ctx.network().serverName()))
                .filter(player -> NetworkVisibility.canSee(viewer, ctx.network(), player))
                .isPresent();
    }

    @Override
    public GuiItem icon(Player viewer, PlayerRef target) {
        return GuiItem.of(Material.LEAD, render("network.gui.player.bring"),
                List.of(render("network.gui.player.bring-lore", ctx.network().serverName())));
    }

    @Override
    public void onClick(GuiClickContext context, PlayerRef target) {
        Player viewer = context.viewer();
        GuiActions.runAndReport(ctx.actionExecutor(), ctx.scheduler(), ctx.messages(), viewer,
                NetworkActions.BRING, target.name(), null, viewer::closeInventory);
    }

    private Component render(String key, Object... args) {
        return ComponentMessages.render(ctx.messages().get(MessageKey.of(key), args));
    }
}

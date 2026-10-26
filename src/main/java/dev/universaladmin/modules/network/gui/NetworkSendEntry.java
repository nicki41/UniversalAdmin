package dev.universaladmin.modules.network.gui;

import dev.universaladmin.core.PlayerRef;
import dev.universaladmin.gui.GuiClickContext;
import dev.universaladmin.gui.GuiItem;
import dev.universaladmin.gui.PlayerMenuEntry;
import dev.universaladmin.localization.ComponentMessages;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.modules.network.NetworkPermissions;
import dev.universaladmin.network.NetworkPlayer;
import dev.universaladmin.network.NetworkVisibility;
import dev.universaladmin.permission.PermissionNode;
import java.util.List;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** On the profile of any player online in the network - here or elsewhere: send them to another server. */
public final class NetworkSendEntry implements PlayerMenuEntry {

    private final NetworkGuiContext ctx;

    public NetworkSendEntry(NetworkGuiContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public String id() {
        return "core:network.send";
    }

    @Override
    public int order() {
        return 7;
    }

    @Override
    public PermissionNode permission() {
        return NetworkPermissions.SEND;
    }

    @Override
    public boolean visible(Player viewer, PlayerRef target) {
        return located(viewer, target)
                .filter(player -> ctx.network().servers().stream()
                        .anyMatch(server -> server.online() && !server.name().equalsIgnoreCase(player.server())))
                .isPresent();
    }

    @Override
    public GuiItem icon(Player viewer, PlayerRef target) {
        return GuiItem.of(Material.COMPASS, render("network.gui.player.send"), List.of(render("network.gui.player.send-lore")));
    }

    @Override
    public void onClick(GuiClickContext context, PlayerRef target) {
        located(context.viewer(), target).ifPresent(player -> NetworkPlayerPage.chooseServer(ctx, context.viewer(), player));
    }

    /** Where the player is in the network, if the network knows them and the viewer may see them. */
    private Optional<NetworkPlayer> located(Player viewer, PlayerRef target) {
        if (!ctx.network().enabled()) {
            return Optional.empty();
        }
        return ctx.network().player(target.id()).filter(player -> NetworkVisibility.canSee(viewer, ctx.network(), player));
    }

    private Component render(String key, Object... args) {
        return ComponentMessages.render(ctx.messages().get(MessageKey.of(key), args));
    }
}

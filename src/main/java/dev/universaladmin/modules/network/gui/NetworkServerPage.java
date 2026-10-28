package dev.universaladmin.modules.network.gui;

import dev.universaladmin.gui.AbstractListGuiPage;
import dev.universaladmin.gui.GuiActions;
import dev.universaladmin.gui.GuiButton;
import dev.universaladmin.gui.GuiClickContext;
import dev.universaladmin.gui.GuiItem;
import dev.universaladmin.gui.GuiLayout;
import dev.universaladmin.gui.GuiPageId;
import dev.universaladmin.gui.GuiRenderContext;
import dev.universaladmin.modules.network.NetworkPermissions;
import dev.universaladmin.modules.network.action.NetworkActions;
import dev.universaladmin.network.NetworkPlayer;
import dev.universaladmin.network.NetworkVisibility;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** The players on one server of the network; click one for what can be done with them. */
public final class NetworkServerPage extends AbstractListGuiPage<NetworkPlayer> {

    public static final GuiPageId ID = GuiPageId.core("network.server");

    private static final int CONNECT_SLOT = GuiLayout.TOOL_RIGHT_3;

    private final NetworkGuiContext ctx;
    private final String server;

    public NetworkServerPage(NetworkGuiContext ctx, String server) {
        super(ID, ctx.framework(), ctx.messages(), ctx.scheduler());
        this.ctx = ctx;
        this.server = server;
    }

    @Override
    protected Component title(Player viewer) {
        return text("network.gui.server-title", server);
    }

    @Override
    protected CompletableFuture<List<NetworkPlayer>> loadItems(Player viewer) {
        return CompletableFuture.completedFuture(NetworkVisibility.players(viewer, ctx.network()).stream()
                .filter(player -> player.server().equalsIgnoreCase(server))
                .toList());
    }

    @Override
    protected void renderControls(GuiRenderContext context) {
        if (server.equalsIgnoreCase(ctx.network().serverName())) {
            return;
        }
        context.view().place(CONNECT_SLOT, new GuiButton(GuiItem.of(Material.ENDER_PEARL, text("network.gui.connect", server),
                List.of(text("network.gui.connect-lore", server))), NetworkPermissions.GOTO, click -> {
                    Player viewer = click.viewer();
                    GuiActions.runAndReport(ctx.actionExecutor(), ctx.scheduler(), messages, viewer,
                            NetworkActions.CONNECT, server, null, viewer::closeInventory);
                }), context.viewer());
    }

    @Override
    protected GuiItem render(NetworkPlayer player) {
        List<Component> lore = new ArrayList<>();
        if (player.since() != null) {
            lore.add(text("network.gui.player.since", NetworkHomePage.since(player.since())));
        }
        lore.add(text("network.gui.player.click"));
        Component name = text("network.gui.player.name", player.name());
        return player.id() == null
                ? GuiItem.of(Material.PLAYER_HEAD, name, lore)
                : GuiItem.playerHead(Bukkit.getOfflinePlayer(player.id()), name, lore);
    }

    @Override
    protected void onSelect(GuiClickContext click, NetworkPlayer player) {
        click.open(new NetworkPlayerPage(ctx, player));
    }
}

package dev.universaladmin.modules.network.gui;

import dev.universaladmin.action.ActionId;
import dev.universaladmin.core.PlayerRef;
import dev.universaladmin.gui.AbstractGuiPage;
import dev.universaladmin.gui.GuiActions;
import dev.universaladmin.gui.GuiButton;
import dev.universaladmin.gui.GuiClickContext;
import dev.universaladmin.gui.GuiItem;
import dev.universaladmin.gui.GuiLayout;
import dev.universaladmin.gui.GuiPageId;
import dev.universaladmin.gui.GuiRenderContext;
import dev.universaladmin.gui.GuiView;
import dev.universaladmin.gui.SelectionDialog;
import dev.universaladmin.localization.ComponentMessages;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.modules.network.NetworkPermissions;
import dev.universaladmin.modules.network.action.NetworkActions;
import dev.universaladmin.modules.players.gui.PlayersGui;
import dev.universaladmin.network.NetworkPlayer;
import dev.universaladmin.network.NetworkServer;
import dev.universaladmin.permission.PermissionNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * One player somewhere in the network: go to them, bring them here, send
 * them to another server, or open their profile (punishments, notes,
 * reports - the database is shared).
 */
public final class NetworkPlayerPage extends AbstractGuiPage {

    public static final GuiPageId ID = GuiPageId.core("network.player");

    /** The Players module's profile permission - checked by its own page as well. */
    private static final PermissionNode PROFILE = PermissionNode.core("players.view");

    private final NetworkGuiContext ctx;
    private final NetworkPlayer player;

    public NetworkPlayerPage(NetworkGuiContext ctx, NetworkPlayer player) {
        super(ID, ctx.framework(), ctx.messages());
        this.ctx = ctx;
        this.player = player;
    }

    @Override
    protected boolean refreshable() {
        return false;
    }

    @Override
    protected Component title(Player viewer) {
        return text("network.gui.player.title", player.name());
    }

    @Override
    protected void renderContent(GuiRenderContext context) {
        GuiView view = context.view();
        Player viewer = context.viewer();
        List<Component> info = new ArrayList<>();
        info.add(text("network.gui.player.on-server", player.server()));
        if (player.since() != null) {
            info.add(text("network.gui.player.since", NetworkHomePage.since(player.since())));
        }
        if (player.id() == null) {
            info.add(text("network.gui.player.no-ua"));
        }
        Component name = text("network.gui.player.name", player.name());
        view.place(GuiLayout.contentSlot(4), player.id() == null
                ? GuiItem.of(Material.PLAYER_HEAD, name, info)
                : GuiItem.playerHead(Bukkit.getOfflinePlayer(player.id()), name, info));

        boolean here = player.server().equalsIgnoreCase(ctx.network().serverName());
        view.place(GuiLayout.contentSlot(20), new GuiButton(GuiItem.of(Material.ENDER_PEARL, text("network.gui.player.goto"),
                List.of(text(here ? "network.gui.player.goto-here" : "network.gui.player.goto-lore", player.server()))),
                NetworkPermissions.GOTO, click -> move(click, NetworkActions.GOTO, player.name())), viewer);
        if (!here) {
            view.place(GuiLayout.contentSlot(21), new GuiButton(GuiItem.of(Material.LEAD, text("network.gui.player.bring"),
                    List.of(text("network.gui.player.bring-lore", ctx.network().serverName()))),
                    NetworkPermissions.SEND, click -> move(click, NetworkActions.BRING, player.name())), viewer);
        }
        view.place(GuiLayout.contentSlot(22), new GuiButton(GuiItem.of(Material.COMPASS, text("network.gui.player.send"),
                List.of(text("network.gui.player.send-lore"))), NetworkPermissions.SEND,
                click -> chooseServer(ctx, click.viewer(), player)), viewer);
        Optional<PlayersGui> profiles = ctx.services().get(PlayersGui.class);
        if (player.id() != null && profiles.isPresent()) {
            view.place(GuiLayout.contentSlot(24), new GuiButton(GuiItem.of(Material.BOOK, text("network.gui.player.profile"),
                    List.of(text("network.gui.player.profile-lore"))), PROFILE,
                    click -> profiles.get().openProfile(click.viewer(), new PlayerRef(player.id(), player.name()))), viewer);
        }
    }

    private void move(GuiClickContext click, ActionId action, Object input) {
        Player viewer = click.viewer();
        GuiActions.runAndReport(ctx.actionExecutor(), ctx.scheduler(), messages, viewer, action, input, null, viewer::closeInventory);
    }

    /** Lets {@code viewer} pick an online server other than the player's and sends the player there - also {@link NetworkSendEntry}'s click. */
    static void chooseServer(NetworkGuiContext ctx, Player viewer, NetworkPlayer player) {
        List<NetworkServer> targets = ctx.network().servers().stream()
                .filter(NetworkServer::online)
                .filter(server -> !server.name().equalsIgnoreCase(player.server()))
                .toList();
        SelectionDialog.open(viewer, ctx.framework(), ctx.messages(), ctx.scheduler(),
                render(ctx, "network.gui.player.send-title", player.name()), targets,
                server -> GuiItem.of(server.universalAdmin() ? Material.EMERALD_BLOCK : Material.QUARTZ_BLOCK,
                        render(ctx, "network.gui.server.name-online", server.name()),
                        List.of(render(ctx, "network.gui.server.players-only", Integer.toString(server.playerCount())))),
                (choice, server) -> GuiActions.runAndReport(ctx.actionExecutor(), ctx.scheduler(), ctx.messages(), choice.viewer(),
                        NetworkActions.SEND, new NetworkActions.SendInput(player.name(), server.name()), null,
                        () -> choice.viewer().closeInventory()));
    }

    private static Component render(NetworkGuiContext ctx, String key, Object... args) {
        return ComponentMessages.render(ctx.messages().get(MessageKey.of(key), args));
    }
}

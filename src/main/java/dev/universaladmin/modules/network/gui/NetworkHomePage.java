package dev.universaladmin.modules.network.gui;

import dev.universaladmin.core.time.DurationParser;
import dev.universaladmin.gui.AbstractListGuiPage;
import dev.universaladmin.gui.GuiActions;
import dev.universaladmin.gui.GuiButton;
import dev.universaladmin.gui.GuiClickContext;
import dev.universaladmin.gui.GuiClickType;
import dev.universaladmin.gui.GuiItem;
import dev.universaladmin.gui.GuiLayout;
import dev.universaladmin.gui.GuiPageId;
import dev.universaladmin.gui.GuiRenderContext;
import dev.universaladmin.gui.GuiTextInput;
import dev.universaladmin.localization.MessageService;
import dev.universaladmin.modules.network.NetworkPermissions;
import dev.universaladmin.modules.network.action.NetworkActions;
import dev.universaladmin.network.NetworkServer;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * {@code network.home} - every server of the network: the ones running
 * UniversalAdmin with players, TPS and version, the ones without (asked from
 * the proxy) with their player count. Click a server for its players;
 * right-click to go there.
 */
public final class NetworkHomePage extends AbstractListGuiPage<NetworkServer> {

    public static final GuiPageId ID = GuiPageId.core("network.home");

    private static final int SUMMARY_SLOT = GuiLayout.TOOL_LEFT_1;
    private static final int ALERT_SLOT = GuiLayout.TOOL_RIGHT_3;

    private final NetworkGuiContext ctx;

    public NetworkHomePage(NetworkGuiContext ctx) {
        super(ID, ctx.framework(), ctx.messages(), ctx.scheduler());
        this.ctx = ctx;
    }

    @Override
    protected Component title(Player viewer) {
        return text("network.gui.title");
    }

    @Override
    protected CompletableFuture<List<NetworkServer>> loadItems(Player viewer) {
        return CompletableFuture.completedFuture(ctx.network().servers());
    }

    @Override
    protected void renderControls(GuiRenderContext context) {
        List<NetworkServer> servers = ctx.network().servers();
        int players = servers.stream().mapToInt(NetworkServer::playerCount).sum();
        long withUa = servers.stream().filter(server -> server.universalAdmin() && server.online()).count();
        context.view().place(SUMMARY_SLOT, GuiItem.of(Material.ENDER_EYE, text("network.gui.summary"), List.of(
                text("network.gui.summary-servers", Integer.toString(servers.size()), Long.toString(withUa)),
                text("network.gui.summary-players", Integer.toString(players)),
                text("network.gui.summary-here", ctx.network().serverName()))));
        context.view().place(ALERT_SLOT, new GuiButton(GuiItem.of(Material.BELL, text("network.gui.alert"),
                List.of(text("network.gui.alert-lore"))), NetworkPermissions.ALERT, this::promptAlert), context.viewer());
    }

    @Override
    protected GuiItem render(NetworkServer server) {
        boolean here = server.name().equalsIgnoreCase(ctx.network().serverName());
        List<Component> lore = new ArrayList<>();
        Material material;
        Component name;
        if (!server.universalAdmin()) {
            material = Material.QUARTZ_BLOCK;
            name = text("network.gui.server.name-other", server.name());
            lore.add(text("network.gui.server.players-only", Integer.toString(server.playerCount())));
            lore.add(text("network.gui.server.no-ua"));
        } else if (!server.online()) {
            material = Material.REDSTONE_BLOCK;
            name = text("network.gui.server.name-offline", server.name());
            lore.add(text("network.gui.server.last-seen", since(server.lastSeen())));
        } else {
            material = here ? Material.BEACON : Material.EMERALD_BLOCK;
            name = text("network.gui.server.name-online", server.name());
            lore.add(text("network.gui.server.players", Integer.toString(server.playerCount()), Integer.toString(server.maxPlayers())));
            lore.add(text("network.gui.server.tps", MessageService.markup(tps(server.tps()))));
            lore.add(text("network.gui.server.version", server.version() == null ? "?" : server.version()));
        }
        lore.add(Component.empty());
        if (here) {
            lore.add(text("network.gui.server.here"));
        }
        lore.add(text("network.gui.server.click"));
        if (!here && server.online()) {
            lore.add(text("network.gui.server.right-click"));
        }
        return GuiItem.of(material, name, lore);
    }

    @Override
    protected void onSelect(GuiClickContext click, NetworkServer server) {
        boolean here = server.name().equalsIgnoreCase(ctx.network().serverName());
        if (click.clickType() == GuiClickType.RIGHT && !here && server.online()) {
            Player viewer = click.viewer();
            GuiActions.runAndReport(ctx.actionExecutor(), ctx.scheduler(), messages, viewer,
                    NetworkActions.CONNECT, server.name(), null, viewer::closeInventory);
            return;
        }
        click.open(new NetworkServerPage(ctx, server.name()));
    }

    private void promptAlert(GuiClickContext click) {
        Player viewer = click.viewer();
        GuiTextInput.request(viewer, text("network.gui.alert"), text("network.gui.alert-prompt"), "",
                text("gui.confirm"), text("gui.cancel"),
                typed -> {
                    if (typed.isBlank()) {
                        this.open(viewer);
                        return;
                    }
                    GuiActions.runAndReport(ctx.actionExecutor(), ctx.scheduler(), messages, viewer,
                            NetworkActions.ALERT, typed, null, () -> this.open(viewer));
                },
                () -> this.open(viewer));
    }

    /** Green from 18, yellow from 15, red below. */
    static String tps(double tps) {
        String colour = tps >= 18 ? "green" : tps >= 15 ? "yellow" : "red";
        return "<" + colour + ">" + String.format(Locale.ROOT, "%.1f", tps) + "</" + colour + ">";
    }

    static String since(Instant instant) {
        return instant == null ? "?" : DurationParser.format(Duration.between(instant, Instant.now()));
    }
}

package dev.universaladmin.modules.server.gui;

import dev.universaladmin.action.ActionId;
import dev.universaladmin.gui.AbstractGuiPage;
import dev.universaladmin.gui.ConfirmationDialog;
import dev.universaladmin.gui.GuiButton;
import dev.universaladmin.gui.GuiItem;
import dev.universaladmin.gui.GuiLayout;
import dev.universaladmin.gui.GuiPageId;
import dev.universaladmin.gui.GuiRenderContext;
import dev.universaladmin.gui.GuiTextInput;
import dev.universaladmin.gui.GuiView;
import dev.universaladmin.modules.server.MaintenanceState;
import dev.universaladmin.modules.server.ServerDashboardSnapshot;
import dev.universaladmin.modules.server.ServerLifecycleService;
import dev.universaladmin.modules.server.ServerPermissions;
import dev.universaladmin.modules.server.action.BroadcastTitleInput;
import dev.universaladmin.modules.server.action.ServerActionIds;
import dev.universaladmin.settings.CoreSettings;
import java.time.Duration;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * {@code server.home} - the Server module's dashboard: live environment info
 * plus navigation into broadcast/maintenance/shutdown/restart, registered by
 * {@code ServerModule} in place of the {@code PlaceholderGuiPage} {@code
 * UniversalAdminPlugin} used to register for this id (same precedent as
 * {@code players.home}/{@code moderation.home}).
 */
public final class ServerHomePage extends AbstractGuiPage {

    public static final GuiPageId ID = GuiPageId.core("server.home");

    private static final int TILE_START = GuiLayout.CONTENT_START_SLOT;

    private final ServerGuiContext ctx;

    public ServerHomePage(ServerGuiContext ctx) {
        super(ID, ctx.framework(), ctx.messages());
        this.ctx = ctx;
    }

    @Override
    protected Component title(Player viewer) {
        return text("server.gui.home.title");
    }

    @Override
    protected void renderContent(GuiRenderContext context) {
        GuiView view = context.view();
        Player viewer = context.viewer();

        renderDashboard(view);
        renderMaintenanceTile(view);
        renderNavigation(view, viewer);
        renderPendingBanner(view, viewer);
    }

    private void renderDashboard(GuiView view) {
        ServerDashboardSnapshot snapshot = ctx.dashboardService().snapshot();
        int slot = TILE_START;
        view.place(slot++, GuiItem.of(Material.BOOK, text("server.gui.home.version"), List.of(
                text("server.gui.home.version-lore", snapshot.pluginVersion(), snapshot.paperVersion(),
                        snapshot.minecraftVersion(), snapshot.javaVersion()))));
        view.place(slot++, GuiItem.of(Material.CLOCK, text("server.gui.home.uptime"), List.of(
                Component.text(formatUptime(snapshot.uptime())))));
        view.place(slot++, GuiItem.of(Material.PLAYER_HEAD, text("server.gui.home.players"), List.of(
                Component.text(snapshot.onlinePlayers() + " / " + snapshot.maxPlayers()))));
        view.place(slot++, GuiItem.of(Material.GRASS_BLOCK, text("server.gui.home.worlds"), List.of(
                Component.text(snapshot.worldCount()))));
        view.place(slot++, GuiItem.of(Material.REDSTONE, text("server.gui.home.memory"), List.of(
                Component.text(formatMegabytes(snapshot.usedMemoryBytes()) + " MB / " + formatMegabytes(snapshot.maxMemoryBytes()) + " MB"))));
        Component cpuLoad = snapshot.cpuLoadPercent().isPresent()
                ? Component.text(String.format("%.1f%%", snapshot.cpuLoadPercent().getAsDouble()))
                : text("server.gui.home.cpu-unavailable");
        view.place(slot++, GuiItem.of(Material.COMPARATOR, text("server.gui.home.cpu"), List.of(
                cpuLoad, Component.text(snapshot.availableProcessors() + " cores"))));
        view.place(slot++, GuiItem.of(Material.ENDER_CHEST, text("server.gui.home.database"), List.of(
                Component.text(snapshot.databaseStatus().name()))));
        view.place(slot, GuiItem.of(Material.CHEST, text("server.gui.home.modules"), List.of(
                Component.text(String.join(", ", snapshot.enabledModules())))));
    }

    private void renderMaintenanceTile(GuiView view) {
        MaintenanceState state = ctx.maintenanceService().current();
        Material material = state.enabled() ? Material.RED_WOOL : Material.LIME_WOOL;
        List<Component> lore = state.enabled() && state.reason() != null && !state.reason().isBlank()
                ? List.of(text("server.gui.home.maintenance-reason", state.reason()))
                : List.of();
        view.place(TILE_START + GuiLayout.COLUMNS,
                GuiItem.of(material, text(state.enabled() ? "server.gui.home.maintenance-on" : "server.gui.home.maintenance-off"), lore));
    }

    private void renderNavigation(GuiView view, Player viewer) {
        int row2 = TILE_START + GuiLayout.COLUMNS + 1;
        view.place(row2, new GuiButton(GuiItem.of(Material.PAPER, text("server.gui.home.broadcast-message")),
                ServerPermissions.BROADCAST, clickCtx -> promptBroadcastMessage(clickCtx.viewer())), viewer);
        view.place(row2 + 1, new GuiButton(GuiItem.of(Material.OAK_SIGN, text("server.gui.home.broadcast-title")),
                ServerPermissions.BROADCAST, clickCtx -> promptBroadcastTitle(clickCtx.viewer())), viewer);
        view.place(row2 + 2, new GuiButton(GuiItem.of(Material.NAME_TAG, text("server.gui.home.broadcast-actionbar")),
                ServerPermissions.BROADCAST, clickCtx -> promptBroadcastActionbar(clickCtx.viewer())), viewer);
        view.place(row2 + 3, new GuiButton(GuiItem.of(Material.IRON_BARS, text("server.gui.home.maintenance-manage")),
                ServerPermissions.MAINTENANCE, clickCtx -> clickCtx.open(new ServerMaintenancePage(ctx))), viewer);

        int row3 = TILE_START + (2 * GuiLayout.COLUMNS);
        view.place(row3, new GuiButton(GuiItem.of(Material.MAGMA_BLOCK, text("server.gui.home.shutdown")),
                ServerPermissions.SHUTDOWN, clickCtx -> promptLifecycle(clickCtx.viewer(), true)), viewer);
        view.place(row3 + 1, new GuiButton(GuiItem.of(Material.RESPAWN_ANCHOR, text("server.gui.home.restart")),
                ServerPermissions.RESTART, clickCtx -> promptLifecycle(clickCtx.viewer(), false)), viewer);
    }

    private void renderPendingBanner(GuiView view, Player viewer) {
        ServerLifecycleService.PendingSnapshot pending = ctx.lifecycleService().pending();
        if (pending.action() == ServerLifecycleService.PendingAction.NONE) {
            return;
        }
        boolean isShutdown = pending.action() == ServerLifecycleService.PendingAction.SHUTDOWN;
        ActionId cancelId = isShutdown ? ServerActionIds.CANCEL_SHUTDOWN : ServerActionIds.CANCEL_RESTART;
        if (!viewer.hasPermission((isShutdown ? ServerPermissions.SHUTDOWN : ServerPermissions.RESTART).value())) {
            return;
        }
        int row3 = TILE_START + (2 * GuiLayout.COLUMNS);
        view.place(row3 + 3, new GuiButton(GuiItem.of(Material.BARRIER,
                text(isShutdown ? "server.gui.home.pending-shutdown" : "server.gui.home.pending-restart", pending.remainingSeconds())),
                null, clickCtx -> ServerGuiActions.<Void>runAction(ctx, clickCtx.viewer(), cancelId, null, () -> this.open(clickCtx.viewer()))),
                viewer);
    }

    private void promptBroadcastMessage(Player viewer) {
        GuiTextInput.request(viewer, text("server.gui.home.broadcast-message"), text("server.gui.prompt.message"), "",
                text("gui.confirm"), text("gui.cancel"),
                submitted -> submitOrReopen(viewer, submitted,
                        message -> ServerGuiActions.runAction(ctx, viewer, ServerActionIds.BROADCAST_MESSAGE,
                                message, () -> this.open(viewer))),
                () -> this.open(viewer));
    }

    private void promptBroadcastTitle(Player viewer) {
        GuiTextInput.request(viewer, text("server.gui.home.broadcast-title"), text("server.gui.prompt.title"), "",
                text("gui.confirm"), text("gui.cancel"),
                title -> {
                    if (title == null || title.isBlank()) {
                        this.open(viewer);
                        return;
                    }
                    GuiTextInput.request(viewer, text("server.gui.home.broadcast-title"), text("server.gui.prompt.subtitle"), "",
                            text("gui.confirm"), text("gui.cancel"),
                            subtitle -> ServerGuiActions.runAction(ctx, viewer, ServerActionIds.BROADCAST_TITLE,
                                    new BroadcastTitleInput(title.trim(), subtitle), () -> this.open(viewer)),
                            () -> this.open(viewer));
                },
                () -> this.open(viewer));
    }

    private void promptBroadcastActionbar(Player viewer) {
        GuiTextInput.request(viewer, text("server.gui.home.broadcast-actionbar"), text("server.gui.prompt.message"), "",
                text("gui.confirm"), text("gui.cancel"),
                submitted -> submitOrReopen(viewer, submitted,
                        message -> ServerGuiActions.runAction(ctx, viewer, ServerActionIds.BROADCAST_ACTIONBAR,
                                message, () -> this.open(viewer))),
                () -> this.open(viewer));
    }

    private void promptLifecycle(Player viewer, boolean shutdown) {
        GuiTextInput.request(viewer, text(shutdown ? "server.gui.home.shutdown" : "server.gui.home.restart"),
                text("server.gui.prompt.reason-optional"), "", text("gui.confirm"), text("gui.cancel"),
                reason -> confirmLifecycle(viewer, shutdown, reason == null || reason.isBlank() ? null : reason.trim()),
                () -> this.open(viewer));
    }

    private void confirmLifecycle(Player viewer, boolean shutdown, String reason) {
        ActionId actionId = shutdown ? ServerActionIds.SHUTDOWN : ServerActionIds.RESTART;
        Runnable apply = () -> ServerGuiActions.runAction(ctx, viewer, actionId, reason, () -> this.open(viewer));
        if (!ctx.settings().get(CoreSettings.GUI_CONFIRMATIONS)) {
            apply.run();
            return;
        }
        ConfirmationDialog.open(viewer, framework, messages, text(shutdown ? "server.gui.home.shutdown" : "server.gui.home.restart"),
                List.of(text(shutdown ? "server.gui.confirm.shutdown" : "server.gui.confirm.restart")),
                ConfirmationDialog.DangerLevel.DANGEROUS, confirmCtx -> apply.run(), confirmCtx -> confirmCtx.back());
    }

    private void submitOrReopen(Player viewer, String submitted, java.util.function.Consumer<String> onNonBlank) {
        if (submitted == null || submitted.isBlank()) {
            this.open(viewer);
            return;
        }
        onNonBlank.accept(submitted.trim());
    }

    private String formatUptime(Duration uptime) {
        long seconds = uptime.toSeconds();
        return "%dh %dm %ds".formatted(seconds / 3600, (seconds % 3600) / 60, seconds % 60);
    }

    private String formatMegabytes(long bytes) {
        return String.valueOf(bytes / (1024 * 1024));
    }
}

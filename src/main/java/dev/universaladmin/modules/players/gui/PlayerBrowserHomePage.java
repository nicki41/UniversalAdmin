package dev.universaladmin.modules.players.gui;

import dev.universaladmin.action.ActionExecutor;
import dev.universaladmin.core.ServiceRegistry;
import dev.universaladmin.gui.AbstractGuiPage;
import dev.universaladmin.gui.GuiButton;
import dev.universaladmin.gui.GuiFramework;
import dev.universaladmin.gui.GuiItem;
import dev.universaladmin.gui.GuiLayout;
import dev.universaladmin.gui.GuiPageId;
import dev.universaladmin.gui.GuiRenderContext;
import dev.universaladmin.gui.GuiView;
import dev.universaladmin.localization.MessageService;
import dev.universaladmin.modules.players.PlayerService;
import dev.universaladmin.scheduler.TaskScheduler;
import dev.universaladmin.settings.SettingsService;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * {@code players.home} - the Player Browser landing page (Online / Offline /
 * Recently Seen / Search), registered by {@code PlayersModule} in place of
 * the {@code PlaceholderGuiPage} {@code UniversalAdminPlugin} used to
 * register for this id. The only page in this package built from raw
 * dependencies rather than an existing {@link PlayerGuiContext} - it's the
 * entry point {@code PlayersModule} (a different package - {@link
 * PlayerGuiContext} is package-private) constructs directly; every page it
 * opens from here on passes the already-built context along.
 */
public final class PlayerBrowserHomePage extends AbstractGuiPage {

    public static final GuiPageId ID = GuiPageId.core("players.home");

    private final PlayerGuiContext ctx;

    public PlayerBrowserHomePage(
            GuiFramework framework, MessageService messages, TaskScheduler scheduler,
            PlayerService playerService, ActionExecutor actionExecutor, SettingsService settings, ServiceRegistry services) {
        super(ID, framework, messages);
        this.ctx = new PlayerGuiContext(framework, messages, scheduler, playerService, actionExecutor, settings, services);
    }

    @Override
    protected boolean refreshable() {
        return false;
    }

    @Override
    protected Component title(Player viewer) {
        return text("players.gui.home.title");
    }

    @Override
    protected void renderContent(GuiRenderContext context) {
        GuiView view = context.view();
        Player viewer = context.viewer();
        int slot = GuiLayout.CONTENT_START_SLOT;

        view.place(slot++, GuiButton.of(
                GuiItem.of(Material.LIME_WOOL, text("players.gui.home.online"), List.of(text("players.gui.home.online-lore"))),
                clickCtx -> clickCtx.open(new OnlinePlayersListPage(ctx))), viewer);
        view.place(slot++, GuiButton.of(
                GuiItem.of(Material.GRAY_WOOL, text("players.gui.home.offline"), List.of(text("players.gui.home.offline-lore"))),
                clickCtx -> clickCtx.open(new OfflinePlayersListPage(ctx))), viewer);
        view.place(slot++, GuiButton.of(
                GuiItem.of(Material.CLOCK, text("players.gui.home.recently-seen"), List.of(text("players.gui.home.recently-seen-lore"))),
                clickCtx -> clickCtx.open(new RecentlySeenPlayersListPage(ctx))), viewer);
        view.place(slot, GuiButton.of(
                GuiItem.of(Material.COMPASS, text("players.gui.home.search"), List.of(text("players.gui.home.search-lore"))),
                clickCtx -> clickCtx.open(new OfflinePlayersListPage(ctx))), viewer);
    }
}

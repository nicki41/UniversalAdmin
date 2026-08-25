package dev.universaladmin.modules.players.gui;

import dev.universaladmin.gui.AbstractGuiPage;
import dev.universaladmin.gui.GuiButton;
import dev.universaladmin.gui.GuiItem;
import dev.universaladmin.gui.GuiLayout;
import dev.universaladmin.gui.GuiPageId;
import dev.universaladmin.gui.GuiRenderContext;
import dev.universaladmin.gui.GuiSession;
import dev.universaladmin.gui.GuiTextInput;
import dev.universaladmin.gui.GuiView;
import dev.universaladmin.gui.Pagination;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * Currently-online players with World/Gamemode filter-cycle buttons - the
 * "prepare a World/Gamemode filter" requirement, meaningful here since
 * both are live state only an online player has. Reading {@link
 * Bukkit#getOnlinePlayers()} and filtering/sorting for display is
 * presentation assembly, not business logic (see {@code AuditLogListPage}'s
 * own row rendering for the same shape) - the moment a click needs to
 * *change* something, it goes through an {@code Action} like every other
 * page here. Hand-rolled like {@link OfflinePlayersListPage} for the same
 * "needs extra chrome buttons" reason.
 */
public final class OnlinePlayersListPage extends AbstractGuiPage {

    public static final GuiPageId ID = GuiPageId.core("players.online");

    private static final int WORLD_FILTER_SLOT = 1;
    private static final int GAMEMODE_FILTER_SLOT = 2;
    private static final int SEARCH_SLOT = 3;

    private final PlayerGuiContext ctx;

    public OnlinePlayersListPage(PlayerGuiContext ctx) {
        super(ID, ctx.framework(), ctx.messages());
        this.ctx = ctx;
    }

    @Override
    protected Component title(Player viewer) {
        return text("players.gui.online.title");
    }

    @Override
    protected void renderContent(GuiRenderContext context) {
        GuiView view = context.view();
        Player viewer = context.viewer();
        String worldFilter = worldFilterFor(context.session());
        GameMode gamemodeFilter = gamemodeFilterFor(context.session());
        String query = queryFor(context.session());
        placeWorldFilterButton(view, viewer, worldFilter);
        placeGamemodeFilterButton(view, viewer, gamemodeFilter);
        placeSearchButton(view, viewer, query);

        // Bukkit.getOnlinePlayers() returns Collection<? extends Player> - copy
        // to a concrete List<Player> first so the stream below isn't stuck
        // with an unusable captured wildcard type. Already an in-memory,
        // already-online collection, so the name search below is a plain
        // substring filter - unlike OfflinePlayersListPage's search, there's
        // no database query to run async.
        String needle = query.toLowerCase(Locale.ROOT);
        List<Player> players = new ArrayList<Player>(Bukkit.getOnlinePlayers()).stream()
                .filter(p -> worldFilter == null || worldFilter.equals(p.getWorld().getName()))
                .filter(p -> gamemodeFilter == null || gamemodeFilter == p.getGameMode())
                .filter(p -> needle.isEmpty() || p.getName().toLowerCase(Locale.ROOT).contains(needle))
                .sorted(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();

        if (players.isEmpty()) {
            view.place(GuiLayout.contentSlot(GuiLayout.contentSize() / 2), GuiItem.of(framework.icons().empty(), text("gui.empty")));
            clearPagination(view);
            return;
        }

        String pageAttribute = id().toString() + ".page";
        Pagination<Player> pagination =
                new Pagination<>(players, GuiLayout.contentSize(), context.session().intAttribute(pageAttribute, 0)).clamped();
        context.session().setAttribute(pageAttribute, pagination.currentPage());

        List<Player> pageItems = pagination.currentPageItems();
        for (int i = 0; i < pageItems.size(); i++) {
            Player target = pageItems.get(i);
            view.place(GuiLayout.contentSlot(i),
                    GuiButton.of(PlayerGuiItems.onlinePlayerItem(messages, target),
                            clickCtx -> clickCtx.open(new PlayerProfilePage(ctx, target.getUniqueId(), target.getName()))),
                    viewer);
        }
        renderPaginationControls(view, viewer, pagination, pageAttribute);
    }

    private void placeWorldFilterButton(GuiView view, Player viewer, String worldFilter) {
        Component label = worldFilter == null ? text("players.gui.online.filter-world-all") : Component.text(worldFilter);
        view.place(WORLD_FILTER_SLOT, GuiButton.of(GuiItem.of(Material.GRASS_BLOCK, label), clickCtx -> {
            clickCtx.session().setAttribute(worldFilterKey(), nextWorldFilter(worldFilter));
            clickCtx.session().setAttribute(id().toString() + ".page", 0);
            this.open(clickCtx.viewer());
        }), viewer);
    }

    private void placeGamemodeFilterButton(GuiView view, Player viewer, GameMode gamemodeFilter) {
        Component label = gamemodeFilter == null ? text("players.gui.online.filter-gamemode-all") : Component.text(gamemodeFilter.name());
        view.place(GAMEMODE_FILTER_SLOT, GuiButton.of(GuiItem.of(Material.IRON_PICKAXE, label), clickCtx -> {
            clickCtx.session().setAttribute(gamemodeFilterKey(), nextGamemodeFilter(gamemodeFilter));
            clickCtx.session().setAttribute(id().toString() + ".page", 0);
            this.open(clickCtx.viewer());
        }), viewer);
    }

    private void placeSearchButton(GuiView view, Player viewer, String query) {
        GuiItem item = GuiItem.of(Material.OAK_SIGN,
                text("players.gui.online.search"),
                List.of(query.isBlank()
                        ? text("players.gui.online.search-empty")
                        : Component.text(query, NamedTextColor.YELLOW)));
        view.place(SEARCH_SLOT, GuiButton.of(item, clickCtx -> GuiTextInput.request(
                clickCtx.viewer(),
                text("players.gui.online.search"),
                text("players.gui.online.search-prompt"),
                query,
                text("gui.confirm"),
                text("gui.cancel"),
                submitted -> {
                    clickCtx.session().setAttribute(searchAttributeKey(), submitted == null ? "" : submitted);
                    clickCtx.session().setAttribute(id().toString() + ".page", 0);
                    this.open(clickCtx.viewer());
                },
                () -> { })), viewer);
    }

    private String queryFor(GuiSession session) {
        return session.attribute(searchAttributeKey()).filter(String.class::isInstance).map(String.class::cast).orElse("");
    }

    private String searchAttributeKey() {
        return id().toString() + ".search";
    }

    private String nextWorldFilter(String current) {
        List<String> options = new ArrayList<>();
        options.add(null);
        for (World world : Bukkit.getWorlds()) {
            options.add(world.getName());
        }
        int index = options.indexOf(current);
        return options.get((index + 1) % options.size());
    }

    private GameMode nextGamemodeFilter(GameMode current) {
        GameMode[] modes = GameMode.values();
        if (current == null) {
            return modes[0];
        }
        int next = current.ordinal() + 1;
        return next >= modes.length ? null : modes[next];
    }

    private String worldFilterFor(GuiSession session) {
        return session.attribute(worldFilterKey()).filter(String.class::isInstance).map(String.class::cast).orElse(null);
    }

    private GameMode gamemodeFilterFor(GuiSession session) {
        return session.attribute(gamemodeFilterKey()).filter(GameMode.class::isInstance).map(GameMode.class::cast).orElse(null);
    }

    private String worldFilterKey() {
        return id().toString() + ".world";
    }

    private String gamemodeFilterKey() {
        return id().toString() + ".gamemode";
    }

    private void clearPagination(GuiView view) {
        view.clear(GuiLayout.PREVIOUS_PAGE_SLOT);
        view.clear(GuiLayout.PAGE_INDICATOR_SLOT);
        view.clear(GuiLayout.NEXT_PAGE_SLOT);
    }

    private void renderPaginationControls(GuiView view, Player viewer, Pagination<Player> pagination, String pageAttribute) {
        if (pagination.hasPrevious()) {
            view.place(GuiLayout.PREVIOUS_PAGE_SLOT,
                    GuiButton.of(GuiItem.of(framework.icons().previousPage(), text("gui.previous-page")), clickCtx -> {
                        clickCtx.session().setAttribute(pageAttribute, pagination.currentPage() - 1);
                        this.open(clickCtx.viewer());
                    }),
                    viewer);
        } else {
            view.clear(GuiLayout.PREVIOUS_PAGE_SLOT);
        }
        view.place(GuiLayout.PAGE_INDICATOR_SLOT, GuiItem.of(framework.icons().pageIndicator(),
                text("gui.page-indicator", pagination.displayPage(), pagination.displayMaxPage())));
        if (pagination.hasNext()) {
            view.place(GuiLayout.NEXT_PAGE_SLOT,
                    GuiButton.of(GuiItem.of(framework.icons().nextPage(), text("gui.next-page")), clickCtx -> {
                        clickCtx.session().setAttribute(pageAttribute, pagination.currentPage() + 1);
                        this.open(clickCtx.viewer());
                    }),
                    viewer);
        } else {
            view.clear(GuiLayout.NEXT_PAGE_SLOT);
        }
    }
}

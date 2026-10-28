package dev.universaladmin.modules.players.gui;

import dev.universaladmin.core.PlayerNames;
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
import dev.universaladmin.network.NetworkServer;
import dev.universaladmin.network.NetworkVisibility;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * Currently-online players - in network mode every player of the network,
 * with the server they are on, a server filter and sorting by name or server
 * (players on other servers open the same profile; what needs them here,
 * like the live inventory, is left out there). World/Gamemode filter-cycle
 * buttons - the
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
    private static final int SERVER_FILTER_SLOT = 5;
    private static final int SORT_SLOT = 6;

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
        boolean network = ctx.network().enabled();
        String serverFilter = network ? serverFilterFor(context.session()) : null;
        boolean byServer = network && sortByServer(context.session());
        placeWorldFilterButton(view, viewer, worldFilter);
        placeGamemodeFilterButton(view, viewer, gamemodeFilter);
        placeSearchButton(view, viewer, query);
        if (network) {
            placeServerFilterButton(view, viewer, serverFilter);
            placeSortButton(view, viewer, byServer);
        }

        // Bukkit.getOnlinePlayers() returns Collection<? extends Player> - copy
        // to a concrete List<Player> first so the stream below isn't stuck
        // with an unusable captured wildcard type. Already in memory (the
        // network's players are a snapshot too), so the name search below is
        // a plain substring filter - unlike OfflinePlayersListPage's search,
        // there's no database query to run async.
        String needle = query.toLowerCase(Locale.ROOT);
        String here = network ? ctx.network().serverName() : null;
        List<Row> rows = new ArrayList<>();
        for (Player player : new ArrayList<Player>(Bukkit.getOnlinePlayers())) {
            rows.add(new Row(player.getUniqueId(), player.getName(), here, player));
        }
        if (network) {
            // Players on servers without UniversalAdmin have no UUID and no
            // profile - the Network menu still lists them.
            NetworkVisibility.players(viewer, ctx.network()).stream()
                    .filter(player -> player.id() != null && !player.server().equalsIgnoreCase(here))
                    .forEach(player -> rows.add(new Row(player.id(), player.name(), player.server(), null)));
        }
        Comparator<Row> order = Comparator.comparing(Row::name, String.CASE_INSENSITIVE_ORDER);
        if (byServer) {
            order = Comparator.comparing((Row row) -> row.server() == null ? "" : row.server(), String.CASE_INSENSITIVE_ORDER).thenComparing(order);
        }
        List<Row> players = rows.stream()
                // World and gamemode are only known for players on this server.
                .filter(row -> worldFilter == null || row.local() != null && worldFilter.equals(row.local().getWorld().getName()))
                .filter(row -> gamemodeFilter == null || row.local() != null && gamemodeFilter == row.local().getGameMode())
                .filter(row -> serverFilter == null || serverFilter.equalsIgnoreCase(row.server()))
                .filter(row -> needle.isEmpty() || row.name().toLowerCase(Locale.ROOT).contains(needle)
                        || PlayerNames.revealedTo(viewer, row.id()).filter(real -> real.toLowerCase(Locale.ROOT).contains(needle)).isPresent())
                .sorted(order)
                .toList();

        if (players.isEmpty()) {
            view.place(GuiLayout.contentSlot(GuiLayout.contentSize() / 2), GuiItem.of(framework.icons().empty(), text("gui.empty")));
            clearPagination(view);
            return;
        }

        String pageAttribute = id().toString() + ".page";
        Pagination<Row> pagination =
                new Pagination<>(players, GuiLayout.contentSize(), context.session().intAttribute(pageAttribute, 0)).clamped();
        context.session().setAttribute(pageAttribute, pagination.currentPage());

        List<Row> pageItems = pagination.currentPageItems();
        for (int i = 0; i < pageItems.size(); i++) {
            Row target = pageItems.get(i);
            String realName = PlayerNames.revealedTo(viewer, target.id()).orElse(null);
            GuiItem item = target.local() != null
                    ? PlayerGuiItems.onlinePlayerItem(messages, target.local(), target.server(), realName)
                    : PlayerGuiItems.networkPlayerItem(messages, target.id(), target.name(), target.server(), realName);
            view.place(GuiLayout.contentSlot(i),
                    GuiButton.of(item, clickCtx -> clickCtx.open(new PlayerProfilePage(ctx, target.id(), target.name()))),
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

    private void placeServerFilterButton(GuiView view, Player viewer, String serverFilter) {
        Component label = serverFilter == null
                ? text("players.gui.online.filter-server-all") : text("players.gui.online.filter-server", serverFilter);
        view.place(SERVER_FILTER_SLOT, GuiButton.of(GuiItem.of(Material.ENDER_EYE, label), clickCtx -> {
            clickCtx.session().setAttribute(serverFilterKey(), nextServerFilter(serverFilter));
            clickCtx.session().setAttribute(id().toString() + ".page", 0);
            this.open(clickCtx.viewer());
        }), viewer);
    }

    private void placeSortButton(GuiView view, Player viewer, boolean byServer) {
        GuiItem item = GuiItem.of(Material.HOPPER,
                text(byServer ? "players.gui.online.sort-server" : "players.gui.online.sort-name"),
                List.of(text("players.gui.online.sort-lore")));
        view.place(SORT_SLOT, GuiButton.of(item, clickCtx -> {
            clickCtx.session().setAttribute(sortKey(), !byServer);
            clickCtx.session().setAttribute(id().toString() + ".page", 0);
            this.open(clickCtx.viewer());
        }), viewer);
    }

    /** All servers, then every online one of the network in turn. */
    private String nextServerFilter(String current) {
        List<String> options = new ArrayList<>();
        options.add(null);
        ctx.network().servers().stream().filter(NetworkServer::online).map(NetworkServer::name).sorted(String.CASE_INSENSITIVE_ORDER)
                .forEach(options::add);
        int index = options.indexOf(current);
        return options.get((index + 1) % options.size());
    }

    private String serverFilterFor(GuiSession session) {
        return session.attribute(serverFilterKey()).filter(String.class::isInstance).map(String.class::cast).orElse(null);
    }

    private boolean sortByServer(GuiSession session) {
        return session.attribute(sortKey()).filter(Boolean.class::isInstance).map(Boolean.class::cast).orElse(false);
    }

    private String serverFilterKey() {
        return id().toString() + ".server";
    }

    private String sortKey() {
        return id().toString() + ".sort-server";
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

    private void renderPaginationControls(GuiView view, Player viewer, Pagination<Row> pagination, String pageAttribute) {
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

    /**
     * One line of the list.
     *
     * @param server where the player is ({@code null} outside network mode)
     * @param local  the player, if they are on this server
     */
    private record Row(UUID id, String name, String server, Player local) {
    }
}

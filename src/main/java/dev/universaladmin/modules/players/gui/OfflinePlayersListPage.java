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
import dev.universaladmin.modules.players.PlayerProfile;
import dev.universaladmin.modules.players.PlayerSearchQuery;
import dev.universaladmin.modules.players.PlayerSort;
import dev.universaladmin.modules.players.PlayersSettings;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * Serves both "Offline Players" (browse) and "Search": a session-held query
 * string (empty = plain browse) plus a cycling sort button. Hand-rolled like
 * {@code AuditLogListPage} rather than {@link dev.universaladmin.gui.AbstractListGuiPage}
 * because it needs two extra persistent chrome buttons (search box, sort
 * cycle) that base class's sealed {@code renderContent} has no room for.
 */
public final class OfflinePlayersListPage extends AbstractGuiPage {

    public static final GuiPageId ID = GuiPageId.core("players.offline");

    private static final int SEARCH_SLOT = 2;
    private static final int SORT_SLOT = 6;

    private final PlayerGuiContext ctx;

    public OfflinePlayersListPage(PlayerGuiContext ctx) {
        super(ID, ctx.framework(), ctx.messages());
        this.ctx = ctx;
    }

    @Override
    protected Component title(Player viewer) {
        return text("players.gui.offline.title");
    }

    @Override
    protected void renderContent(GuiRenderContext context) {
        String query = queryFor(context.session());
        PlayerSort sort = sortFor(context.session());
        placeSearchButton(context.view(), context.viewer(), query);
        placeSortButton(context.view(), context.viewer(), sort);
        renderPlaceholder(context.view(), framework.icons().loading(), text("gui.loading"));

        int limit = ctx.settings().get(PlayersSettings.GUI_MAX_RESULTS);
        ctx.playerService().search(new PlayerSearchQuery(query.isBlank() ? null : query, sort, limit))
                .whenComplete((profiles, error) -> ctx.scheduler().runOnMainThread(() -> {
                    if (!stillOpen(context)) {
                        return;
                    }
                    if (error != null) {
                        renderPlaceholder(context.view(), framework.icons().error(), text("gui.error"));
                        return;
                    }
                    renderList(context, profiles, query, sort);
                }));
    }

    private void renderList(GuiRenderContext context, List<PlayerProfile> profiles, String query, PlayerSort sort) {
        GuiView view = context.view();
        Player viewer = context.viewer();
        view.clearContentArea();
        placeSearchButton(view, viewer, query);
        placeSortButton(view, viewer, sort);

        if (profiles.isEmpty()) {
            renderPlaceholder(view, framework.icons().empty(), text("gui.empty"));
            return;
        }

        String pageAttribute = id().toString() + ".page";
        Pagination<PlayerProfile> pagination =
                new Pagination<>(profiles, GuiLayout.contentSize(), context.session().intAttribute(pageAttribute, 0)).clamped();
        context.session().setAttribute(pageAttribute, pagination.currentPage());

        List<PlayerProfile> pageItems = pagination.currentPageItems();
        for (int i = 0; i < pageItems.size(); i++) {
            PlayerProfile profile = pageItems.get(i);
            view.place(GuiLayout.contentSlot(i),
                    GuiButton.of(PlayerGuiItems.offlineProfileItem(messages, profile),
                            clickCtx -> clickCtx.open(new PlayerProfilePage(ctx, profile.id(), profile.lastKnownName()))),
                    viewer);
        }
        renderPaginationControls(view, viewer, pagination, pageAttribute);
    }

    private void placeSearchButton(GuiView view, Player viewer, String query) {
        GuiItem item = GuiItem.of(Material.OAK_SIGN,
                text("players.gui.offline.search"),
                List.of(query.isBlank()
                        ? text("players.gui.offline.search-empty")
                        : Component.text(query, NamedTextColor.YELLOW)));
        view.place(SEARCH_SLOT, GuiButton.of(item, clickCtx -> GuiTextInput.request(
                clickCtx.viewer(),
                text("players.gui.offline.search"),
                text("players.gui.offline.search-prompt"),
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

    private void placeSortButton(GuiView view, Player viewer, PlayerSort sort) {
        GuiItem item = GuiItem.of(Material.HOPPER, text(sortMessageKey(sort)));
        view.place(SORT_SLOT, GuiButton.of(item, clickCtx -> {
            clickCtx.session().setAttribute(sortAttributeKey(), nextSort(sort));
            clickCtx.session().setAttribute(id().toString() + ".page", 0);
            this.open(clickCtx.viewer());
        }), viewer);
    }

    private String queryFor(GuiSession session) {
        return session.attribute(searchAttributeKey()).filter(String.class::isInstance).map(String.class::cast).orElse("");
    }

    private PlayerSort sortFor(GuiSession session) {
        return session.attribute(sortAttributeKey()).filter(PlayerSort.class::isInstance).map(PlayerSort.class::cast)
                .orElse(PlayerSort.NAME_ASC);
    }

    private PlayerSort nextSort(PlayerSort current) {
        return current == PlayerSort.NAME_ASC ? PlayerSort.LAST_SEEN_DESC : PlayerSort.NAME_ASC;
    }

    private String sortMessageKey(PlayerSort sort) {
        return sort == PlayerSort.NAME_ASC ? "players.gui.offline.sort-name" : "players.gui.offline.sort-last-seen";
    }

    private String searchAttributeKey() {
        return id().toString() + ".search";
    }

    private String sortAttributeKey() {
        return id().toString() + ".sort";
    }

    private boolean stillOpen(GuiRenderContext context) {
        Player viewer = context.viewer();
        return viewer.isOnline() && viewer.getOpenInventory().getTopInventory().getHolder() == context.view();
    }

    private void renderPlaceholder(GuiView view, Material material, Component label) {
        view.clearContentArea();
        view.place(GuiLayout.contentSlot(GuiLayout.contentSize() / 2), GuiItem.of(material, label));
    }

    private void renderPaginationControls(GuiView view, Player viewer, Pagination<PlayerProfile> pagination, String pageAttribute) {
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

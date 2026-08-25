package dev.universaladmin.gui;

import dev.universaladmin.localization.MessageService;
import dev.universaladmin.scheduler.TaskScheduler;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * Base for a page that shows an async-loaded, paginated list - the
 * "Async Data" and "Pagination" sections of docs/development/gui-framework.md
 * are both implemented here once, so a feature page only supplies
 * {@link #loadItems} and {@link #render}.
 *
 * <p>Flow on every {@link #open}: render a loading placeholder immediately
 * (so the inventory never appears blank), call {@link #loadItems} on
 * whatever thread the caller's {@link TaskScheduler} runs it on, then hop
 * back to the main thread before touching the {@link GuiView} - Bukkit
 * inventory mutations are never safe off-main-thread, see
 * docs/architecture/threading.md. A page whose viewer navigated away or
 * logged off before the load finished is detected and the result is
 * silently dropped instead of mutating a stale/closed inventory.
 *
 * @param <T> the item type this page lists
 */
public abstract class AbstractListGuiPage<T> extends AbstractGuiPage {

    private final TaskScheduler scheduler;

    protected AbstractListGuiPage(GuiPageId id, GuiFramework framework, MessageService messages, TaskScheduler scheduler) {
        super(id, framework, messages);
        this.scheduler = scheduler;
    }

    /** Loads the full (unpaginated) list for {@code viewer}. Runs off the main thread. */
    protected abstract CompletableFuture<List<T>> loadItems(Player viewer);

    /** Renders one item as it appears in the content area. */
    protected abstract GuiItem render(T item);

    /** Called when an item is clicked. No-op by default - override for a selection/detail flow. */
    protected void onSelect(GuiClickContext context, T item) {
    }

    @Override
    protected final void renderContent(GuiRenderContext context) {
        renderPlaceholder(context.view(), framework.icons().loading(), text("gui.loading"));
        Player viewer = context.viewer();
        loadItems(viewer).whenComplete((items, error) -> scheduler.runOnMainThread(() -> {
            if (!stillOpen(context)) {
                return;
            }
            if (error != null) {
                renderPlaceholder(context.view(), framework.icons().error(), text("gui.error"));
                return;
            }
            renderList(context, items);
        }));
    }

    private boolean stillOpen(GuiRenderContext context) {
        Player viewer = context.viewer();
        return viewer.isOnline() && viewer.getOpenInventory().getTopInventory().getHolder() == context.view();
    }

    private void renderList(GuiRenderContext context, List<T> items) {
        GuiView view = context.view();
        Player viewer = context.viewer();
        view.clearContentArea();

        if (items.isEmpty()) {
            renderPlaceholder(view, framework.icons().empty(), text("gui.empty"));
            return;
        }

        String pageAttribute = pageAttributeKey();
        int requestedPage = context.session().intAttribute(pageAttribute, 0);
        Pagination<T> pagination = new Pagination<>(items, GuiLayout.contentSize(), requestedPage).clamped();
        context.session().setAttribute(pageAttribute, pagination.currentPage());

        List<T> pageItems = pagination.currentPageItems();
        for (int i = 0; i < pageItems.size(); i++) {
            T item = pageItems.get(i);
            view.place(GuiLayout.contentSlot(i), GuiButton.of(render(item), ctx -> onSelect(ctx, item)), viewer);
        }
        renderPaginationControls(view, viewer, pagination, pageAttribute);
    }

    private void renderPaginationControls(GuiView view, Player viewer, Pagination<T> pagination, String pageAttribute) {
        if (pagination.hasPrevious()) {
            view.place(GuiLayout.PREVIOUS_PAGE_SLOT,
                    GuiButton.of(GuiItem.of(framework.icons().previousPage(), text("gui.previous-page")), ctx -> {
                        ctx.session().setAttribute(pageAttribute, pagination.currentPage() - 1);
                        this.open(ctx.viewer());
                    }),
                    viewer);
        } else {
            view.clear(GuiLayout.PREVIOUS_PAGE_SLOT);
        }

        view.place(GuiLayout.PAGE_INDICATOR_SLOT, GuiItem.of(framework.icons().pageIndicator(),
                text("gui.page-indicator", pagination.displayPage(), pagination.displayMaxPage())));

        if (pagination.hasNext()) {
            view.place(GuiLayout.NEXT_PAGE_SLOT,
                    GuiButton.of(GuiItem.of(framework.icons().nextPage(), text("gui.next-page")), ctx -> {
                        ctx.session().setAttribute(pageAttribute, pagination.currentPage() + 1);
                        this.open(ctx.viewer());
                    }),
                    viewer);
        } else {
            view.clear(GuiLayout.NEXT_PAGE_SLOT);
        }
    }

    private void renderPlaceholder(GuiView view, Material material, Component label) {
        view.clearContentArea();
        view.place(GuiLayout.contentSlot(GuiLayout.contentSize() / 2), GuiItem.of(material, label));
    }

    private String pageAttributeKey() {
        return id().toString() + ".page";
    }
}

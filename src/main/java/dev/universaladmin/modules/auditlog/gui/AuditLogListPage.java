package dev.universaladmin.modules.auditlog.gui;

import dev.universaladmin.audit.AuditEvent;
import dev.universaladmin.audit.AuditQuery;
import dev.universaladmin.audit.AuditService;
import dev.universaladmin.gui.AbstractGuiPage;
import dev.universaladmin.gui.GuiButton;
import dev.universaladmin.gui.GuiFramework;
import dev.universaladmin.gui.GuiItem;
import dev.universaladmin.gui.GuiLayout;
import dev.universaladmin.gui.GuiPageId;
import dev.universaladmin.gui.GuiRenderContext;
import dev.universaladmin.gui.GuiSession;
import dev.universaladmin.gui.GuiView;
import dev.universaladmin.gui.Pagination;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.localization.MessageService;
import dev.universaladmin.permission.PermissionNode;
import dev.universaladmin.scheduler.TaskScheduler;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * Newest-first, paginated audit history - the list half of the audit log GUI
 * (see {@link AuditLogDetailPage} for the detail half). Deliberately does
 * <b>not</b> extend {@link dev.universaladmin.gui.AbstractListGuiPage}: that
 * base class seals content rendering to just the list, and this page also
 * needs a persistent filter toggle in the top chrome row - see
 * docs/user/audit-log.md for why a hand-rolled page was the simpler choice
 * here over extending the framework's list base.
 *
 * <p>Loads at most {@link #BATCH_LIMIT} entries matching the current filter
 * (newest first) and paginates over that batch client-side, the same
 * "load once, slice in memory" shape every other list page in this codebase
 * uses (see {@link dev.universaladmin.gui.AbstractListGuiPage}) - not a
 * fully server-paginated view of a potentially huge table, but consistent
 * with the rest of the GUI framework today.
 */
public final class AuditLogListPage extends AbstractGuiPage {

    public static final GuiPageId ID = GuiPageId.core("audit-log.home");

    private static final int FILTER_SLOT = 2;
    private static final int BATCH_LIMIT = 200;
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT).withZone(ZoneId.systemDefault());

    private final AuditService auditService;
    private final TaskScheduler scheduler;
    private final PermissionNode detailsPermission;

    public AuditLogListPage(
            GuiFramework framework, MessageService messages, TaskScheduler scheduler,
            AuditService auditService, PermissionNode detailsPermission) {
        super(ID, framework, messages);
        this.scheduler = scheduler;
        this.auditService = auditService;
        this.detailsPermission = detailsPermission;
    }

    @Override
    protected Component title(Player viewer) {
        return text("audit.gui.title");
    }

    @Override
    protected void renderContent(GuiRenderContext context) {
        Filter filter = filterFor(context.session());
        placeFilterButton(context.view(), context.viewer(), filter);
        renderPlaceholder(context.view(), framework.icons().loading(), text("gui.loading"));

        AuditQuery query = AuditQuery.builder().success(filter.successFilter()).pageSize(BATCH_LIMIT).build();
        auditService.query(query).whenComplete((page, error) -> scheduler.runOnMainThread(() -> {
            if (!stillOpen(context)) {
                return;
            }
            if (error != null) {
                renderPlaceholder(context.view(), framework.icons().error(), text("gui.error"));
                return;
            }
            renderList(context, page.items(), filter);
        }));
    }

    private void renderList(GuiRenderContext context, List<AuditEvent> items, Filter filter) {
        GuiView view = context.view();
        Player viewer = context.viewer();
        view.clearContentArea();
        placeFilterButton(view, viewer, filter);

        if (items.isEmpty()) {
            renderPlaceholder(view, framework.icons().empty(), text("gui.empty"));
            return;
        }

        String pageAttribute = id().toString() + ".page";
        Pagination<AuditEvent> pagination =
                new Pagination<>(items, GuiLayout.contentSize(), context.session().intAttribute(pageAttribute, 0)).clamped();
        context.session().setAttribute(pageAttribute, pagination.currentPage());

        List<AuditEvent> pageItems = pagination.currentPageItems();
        for (int i = 0; i < pageItems.size(); i++) {
            placeEntry(view, viewer, GuiLayout.contentSlot(i), pageItems.get(i));
        }
        renderPaginationControls(view, viewer, pagination, pageAttribute);
    }

    private void placeEntry(GuiView view, Player viewer, int slot, AuditEvent event) {
        GuiItem item = renderEntry(event);
        // GUI permission is display-only (see docs/development/gui-framework.md#permissions):
        // a viewer without audit.details still sees the row's summary, just
        // not a clickable detail page - the entry itself already required
        // audit.view to be visible at all (gated at the main menu button).
        if (viewer.hasPermission(detailsPermission.value())) {
            view.place(slot, GuiButton.of(item, ctx -> ctx.open(new AuditLogDetailPage(framework, messages, event))), viewer);
        } else {
            view.place(slot, item);
        }
    }

    private GuiItem renderEntry(AuditEvent event) {
        Material material = event.success() ? Material.LIME_DYE : Material.RED_DYE;
        Component name = Component.text(event.type().toString(), event.success() ? NamedTextColor.GREEN : NamedTextColor.RED);
        List<Component> lore = List.of(
                Component.text(TIME_FORMAT.format(event.timestamp()) + "  " + event.source(), NamedTextColor.GRAY),
                Component.text(event.actor().displayName(), NamedTextColor.YELLOW),
                Component.text(event.summary(), NamedTextColor.WHITE));
        return GuiItem.of(material, name, lore);
    }

    private void placeFilterButton(GuiView view, Player viewer, Filter filter) {
        GuiItem item = GuiItem.of(Material.HOPPER, text(filter.messageKey()),
                List.of(Component.text(messages.get(MessageKey.of("audit.gui.filter.hint")), NamedTextColor.GRAY)));
        view.place(FILTER_SLOT, GuiButton.of(item, ctx -> {
            ctx.session().setAttribute(filterAttributeKey(), filter.next());
            ctx.session().setAttribute(id().toString() + ".page", 0);
            this.open(ctx.viewer());
        }), viewer);
    }

    private Filter filterFor(GuiSession session) {
        return session.attribute(filterAttributeKey())
                .filter(Filter.class::isInstance)
                .map(Filter.class::cast)
                .orElse(Filter.ALL);
    }

    private String filterAttributeKey() {
        return id().toString() + ".filter";
    }

    private boolean stillOpen(GuiRenderContext context) {
        Player viewer = context.viewer();
        return viewer.isOnline() && viewer.getOpenInventory().getTopInventory().getHolder() == context.view();
    }

    private void renderPlaceholder(GuiView view, Material material, Component label) {
        view.clearContentArea();
        view.place(GuiLayout.contentSlot(GuiLayout.contentSize() / 2), GuiItem.of(material, label));
    }

    private void renderPaginationControls(GuiView view, Player viewer, Pagination<AuditEvent> pagination, String pageAttribute) {
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

    /** The "Filter-Grundlage" this page ships with - cycles through the one dimension every entry always has: success/failure. */
    private enum Filter {
        ALL,
        SUCCESS_ONLY,
        FAILURES_ONLY;

        Boolean successFilter() {
            return switch (this) {
                case ALL -> null;
                case SUCCESS_ONLY -> Boolean.TRUE;
                case FAILURES_ONLY -> Boolean.FALSE;
            };
        }

        String messageKey() {
            return switch (this) {
                case ALL -> "audit.gui.filter.all";
                case SUCCESS_ONLY -> "audit.gui.filter.success-only";
                case FAILURES_ONLY -> "audit.gui.filter.failures-only";
            };
        }

        Filter next() {
            Filter[] values = values();
            return values[(ordinal() + 1) % values.length];
        }
    }
}

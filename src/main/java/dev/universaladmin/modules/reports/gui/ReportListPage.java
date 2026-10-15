package dev.universaladmin.modules.reports.gui;

import dev.universaladmin.core.PlayerRef;
import dev.universaladmin.gui.AbstractListGuiPage;
import dev.universaladmin.gui.GuiButton;
import dev.universaladmin.gui.GuiClickContext;
import dev.universaladmin.gui.GuiItem;
import dev.universaladmin.gui.GuiLayout;
import dev.universaladmin.gui.GuiPageId;
import dev.universaladmin.gui.GuiRenderContext;
import dev.universaladmin.modules.reports.Report;
import dev.universaladmin.modules.reports.ReportStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * A list of reports: the open ones (the {@code /admin} menu entry), every
 * recent one, or every report about one player (from their profile). The
 * open/all toggle sits in the top row; click a report for its detail page.
 */
public final class ReportListPage extends AbstractListGuiPage<Report> {

    public static final GuiPageId ID = GuiPageId.core("reports.home");

    private static final int LIMIT = 500;
    private static final int FILTER_SLOT = GuiLayout.BACK_SLOT + 2;

    private final ReportsGuiContext ctx;
    private final PlayerRef target;

    /** The open-reports home page. */
    public ReportListPage(ReportsGuiContext ctx) {
        this(ctx, null);
    }

    /** Every report about {@code target}, or the home page if {@code target} is {@code null}. */
    public ReportListPage(ReportsGuiContext ctx, PlayerRef target) {
        super(target == null ? ID : GuiPageId.core("reports.about"), ctx.framework(), ctx.messages(), ctx.scheduler());
        this.ctx = ctx;
        this.target = target;
    }

    @Override
    protected Component title(Player viewer) {
        return target == null ? text("reports.gui.list.title") : text("reports.gui.list.title-about", target.name());
    }

    @Override
    protected void renderControls(GuiRenderContext context) {
        if (target != null) {
            return;
        }
        boolean showAll = showAll(context);
        context.view().place(FILTER_SLOT, GuiButton.of(
                GuiItem.of(Material.HOPPER, text(showAll ? "reports.gui.list.filter-all" : "reports.gui.list.filter-open"),
                        List.of(text("reports.gui.list.filter-hint"))),
                clickCtx -> {
                    clickCtx.session().setAttribute(filterKey(), !showAll);
                    this.open(clickCtx.viewer());
                }), context.viewer());
    }

    @Override
    protected CompletableFuture<List<Report>> loadItems(Player viewer) {
        if (target != null) {
            return ctx.reports().about(target.id(), LIMIT);
        }
        boolean showAll = ctx.framework().sessions().sessionFor(viewer.getUniqueId())
                .attribute(filterKey()).map(Boolean.TRUE::equals).orElse(false);
        return showAll ? ctx.reports().recent(LIMIT) : ctx.reports().open(LIMIT);
    }

    @Override
    protected GuiItem render(Report report) {
        List<Component> lore = new ArrayList<>();
        lore.add(text("reports.gui.field.reason", report.reason()));
        lore.add(text("reports.gui.field.reporter", report.reporterName()));
        lore.add(text("reports.gui.field.age", ReportsGuiContext.age(report)));
        lore.add(text("reports.gui.status." + report.status().name().toLowerCase(Locale.ROOT)));
        if (report.handlerName() != null) {
            lore.add(text("reports.gui.field.handler", report.handlerName()));
        }
        lore.add(text("reports.gui.list.open-hint"));
        return GuiItem.of(iconFor(report.status()),
                Component.text("#" + report.id() + " " + report.targetName(), NamedTextColor.GOLD), lore);
    }

    @Override
    protected void onSelect(GuiClickContext context, Report report) {
        context.open(new ReportDetailPage(ctx, report.id()));
    }

    static Material iconFor(ReportStatus status) {
        return switch (status) {
            case OPEN -> Material.BELL;
            case CLAIMED -> Material.CLOCK;
            case RESOLVED -> Material.LIME_DYE;
            case REJECTED -> Material.GRAY_DYE;
        };
    }

    private boolean showAll(GuiRenderContext context) {
        return context.session().attribute(filterKey()).map(Boolean.TRUE::equals).orElse(false);
    }

    private String filterKey() {
        return ID + ".show-all";
    }
}

package dev.universaladmin.modules.reports.gui;

import dev.universaladmin.core.PlayerRef;
import org.bukkit.entity.Player;

/** Opens Reports pages from outside the GUI package - used by {@code /admin reports}. */
public final class ReportsGui {

    private final ReportsGuiContext ctx;

    public ReportsGui(ReportsGuiContext ctx) {
        this.ctx = ctx;
    }

    public void openList(Player viewer) {
        new ReportListPage(ctx).open(viewer);
    }

    public void openAbout(Player viewer, PlayerRef target) {
        new ReportListPage(ctx, target).open(viewer);
    }

    public void openDetail(Player viewer, long reportId) {
        new ReportDetailPage(ctx, reportId).open(viewer);
    }
}

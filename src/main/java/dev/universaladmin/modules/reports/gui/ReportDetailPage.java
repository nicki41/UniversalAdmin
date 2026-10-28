package dev.universaladmin.modules.reports.gui;

import dev.universaladmin.core.PlayerRef;
import dev.universaladmin.gui.AbstractGuiPage;
import dev.universaladmin.gui.GuiActions;
import dev.universaladmin.gui.GuiButton;
import dev.universaladmin.gui.GuiItem;
import dev.universaladmin.gui.GuiLayout;
import dev.universaladmin.gui.GuiPageId;
import dev.universaladmin.gui.GuiRenderContext;
import dev.universaladmin.gui.GuiTextInput;
import dev.universaladmin.gui.GuiView;
import dev.universaladmin.gui.PlayerMenuEntry;
import dev.universaladmin.localization.ComponentMessages;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.modules.reports.Report;
import dev.universaladmin.modules.reports.ReportsPermissions;
import dev.universaladmin.modules.reports.action.ReportActionIds;
import dev.universaladmin.modules.reports.action.TeleportToReportAction;
import dev.universaladmin.modules.reports.action.UpdateReportAction;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * One report: who reported whom, why, where, and its status - plus the
 * staff actions (claim, resolve, reject, teleport to the player or the
 * report location) and every player-menu button other modules contribute
 * for the reported player (e.g. Moderation's "Moderate"), so a report can be
 * acted on without leaving the page.
 */
final class ReportDetailPage extends AbstractGuiPage {

    private static final GuiPageId ID = GuiPageId.core("reports.detail");

    private final ReportsGuiContext ctx;
    private final long reportId;

    ReportDetailPage(ReportsGuiContext ctx, long reportId) {
        super(ID, ctx.framework(), ctx.messages());
        this.ctx = ctx;
        this.reportId = reportId;
    }

    @Override
    protected Component title(Player viewer) {
        return text("reports.gui.detail.title", String.valueOf(reportId));
    }

    @Override
    protected void renderContent(GuiRenderContext context) {
        GuiView view = context.view();
        view.place(GuiLayout.contentSlot(GuiLayout.contentSize() / 2), GuiItem.of(framework.icons().loading(), text("gui.loading")));
        ctx.reports().find(reportId).whenComplete((found, error) -> ctx.scheduler().runOnMainThread(() -> {
            Player viewer = context.viewer();
            if (!view.isOpenFor(viewer)) {
                return;
            }
            view.clearContentArea();
            if (error != null || found.isEmpty()) {
                view.place(GuiLayout.contentSlot(GuiLayout.contentSize() / 2),
                        GuiItem.of(framework.icons().error(), text(error != null ? "gui.error" : "reports.gui.detail.not-found")));
                return;
            }
            render(view, viewer, found.get());
        }));
    }

    private void render(GuiView view, Player viewer, Report report) {
        view.place(GuiLayout.contentSlot(4), header(report));

        List<GuiButton> actions = new ArrayList<>();
        if (report.status().isOpen()) {
            actions.add(new GuiButton(GuiItem.of(Material.NAME_TAG, text("reports.gui.detail.claim")), ReportsPermissions.MANAGE,
                    ctx2 -> update(ctx2.viewer(), UpdateReportAction.Kind.CLAIM, null)));
            actions.add(new GuiButton(GuiItem.of(Material.LIME_CONCRETE, text("reports.gui.detail.resolve")), ReportsPermissions.MANAGE,
                    ctx2 -> promptNote(ctx2.viewer(), UpdateReportAction.Kind.RESOLVE)));
            actions.add(new GuiButton(GuiItem.of(Material.RED_CONCRETE, text("reports.gui.detail.reject")), ReportsPermissions.MANAGE,
                    ctx2 -> promptNote(ctx2.viewer(), UpdateReportAction.Kind.REJECT)));
        }
        if (Bukkit.getPlayer(report.targetId()) != null) {
            actions.add(new GuiButton(GuiItem.of(Material.ENDER_PEARL, text("reports.gui.detail.teleport-target")), ReportsPermissions.MANAGE,
                    ctx2 -> teleport(ctx2.viewer(), true)));
        }
        if (report.hasLocation()) {
            actions.add(new GuiButton(GuiItem.of(Material.COMPASS, text("reports.gui.detail.teleport-location")), ReportsPermissions.MANAGE,
                    ctx2 -> teleport(ctx2.viewer(), false)));
        }
        int slot = GuiLayout.contentSlot(19);
        for (GuiButton button : actions) {
            if (button.isVisibleTo(viewer)) {
                view.place(slot++, button, viewer);
            }
        }

        // Everything other modules offer for the reported player - Moderate,
        // Notes, an extension's own page - without importing any of them.
        PlayerRef target = new PlayerRef(report.targetId(), report.targetName());
        boolean targetOnline = Bukkit.getPlayer(report.targetId()) != null;
        int menuSlot = GuiLayout.contentSlot(27);
        for (PlayerMenuEntry entry : ctx.playerMenu().entries()) {
            if (menuSlot > GuiLayout.CONTENT_END_SLOT || (entry.requiresOnline() && !targetOnline)) {
                continue;
            }
            GuiButton button = new GuiButton(entry.icon(viewer, target), entry.permission(), click -> entry.onClick(click, target));
            if (button.isVisibleTo(viewer)) {
                view.place(menuSlot++, button, viewer);
            }
        }
    }

    private GuiItem header(Report report) {
        List<Component> lore = new ArrayList<>();
        lore.add(text("reports.gui.field.reason", report.reason()));
        lore.add(text("reports.gui.field.reporter", report.reporterName()));
        lore.add(text("reports.gui.field.created", ReportsGuiContext.formatTime(report.createdAt()), ReportsGuiContext.age(report)));
        lore.add(text("reports.gui.status." + report.status().name().toLowerCase(Locale.ROOT)));
        if (report.handlerName() != null) {
            lore.add(text("reports.gui.field.handler", report.handlerName()));
        }
        if (report.resolution() != null) {
            lore.add(text("reports.gui.field.resolution", report.resolution()));
        }
        if (report.server() != null) {
            lore.add(text("reports.gui.field.server", report.server()));
        }
        if (report.hasLocation()) {
            lore.add(text("reports.gui.field.location", report.world(),
                    Long.toString(Math.round(report.x())), Long.toString(Math.round(report.y())), Long.toString(Math.round(report.z()))));
        }
        return GuiItem.playerHead(Bukkit.getOfflinePlayer(report.targetId()),
                Component.text(report.targetName(), NamedTextColor.GOLD), lore);
    }

    private void promptNote(Player viewer, UpdateReportAction.Kind kind) {
        GuiTextInput.request(viewer, text(kind == UpdateReportAction.Kind.RESOLVE ? "reports.gui.detail.resolve" : "reports.gui.detail.reject"),
                text("reports.gui.detail.prompt-note"), "", text("gui.confirm"), text("gui.cancel"),
                note -> update(viewer, kind, note),
                () -> this.open(viewer));
    }

    private void update(Player viewer, UpdateReportAction.Kind kind, String note) {
        var id = switch (kind) {
            case CLAIM -> ReportActionIds.CLAIM;
            case RESOLVE -> ReportActionIds.RESOLVE;
            case REJECT -> ReportActionIds.REJECT;
        };
        GuiActions.runAndReport(ctx.actionExecutor(), ctx.scheduler(), messages, viewer, id,
                new UpdateReportAction.Input(reportId, note), null, () -> this.open(viewer));
    }

    private void teleport(Player viewer, boolean toTarget) {
        viewer.closeInventory();
        GuiActions.runAndReport(ctx.actionExecutor(), ctx.scheduler(), messages, viewer,
                ReportActionIds.TELEPORT, new TeleportToReportAction.Input(reportId, toTarget), null, () -> { });
    }

}

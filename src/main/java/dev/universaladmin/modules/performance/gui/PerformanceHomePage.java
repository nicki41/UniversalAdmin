package dev.universaladmin.modules.performance.gui;

import dev.universaladmin.gui.AbstractGuiPage;
import dev.universaladmin.gui.GuiButton;
import dev.universaladmin.gui.GuiItem;
import dev.universaladmin.gui.GuiLayout;
import dev.universaladmin.gui.GuiPageId;
import dev.universaladmin.gui.GuiRenderContext;
import dev.universaladmin.gui.GuiView;
import dev.universaladmin.modules.performance.PerformanceSnapshot;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.OptionalDouble;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * {@code performance.home} - the Performance dashboard: read-only tiles from
 * {@link dev.universaladmin.modules.performance.PerformanceSamplingService}'s
 * cache (never recomputed on render, see that class's javadoc), plus
 * navigation into the per-world and entity-overview pages. Same
 * "no shortcuts for a built-in module" shape as {@code ServerHomePage}.
 */
public final class PerformanceHomePage extends AbstractGuiPage {

    public static final GuiPageId ID = GuiPageId.core("performance.home");

    private final PerformanceGuiContext ctx;

    public PerformanceHomePage(PerformanceGuiContext ctx) {
        super(ID, ctx.framework(), ctx.messages());
        this.ctx = ctx;
    }

    @Override
    protected Component title(Player viewer) {
        return text("performance.gui.home.title");
    }

    @Override
    protected void renderContent(GuiRenderContext context) {
        GuiView view = context.view();
        Player viewer = context.viewer();

        renderDashboard(view);
        renderNavigation(view, viewer);
    }

    private void renderDashboard(GuiView view) {
        PerformanceSnapshot snapshot = ctx.samplingService().snapshot();
        OptionalDouble avgTps5m = ctx.samplingService().history().averageTps();

        int slot = GuiLayout.CONTENT_START_SLOT;
        view.place(slot++, GuiItem.of(Material.COMPASS, text("performance.gui.home.tps"), List.of(
                Component.text(String.format(Locale.ROOT, "1m: %.2f | 5m: %.2f | 15m: %.2f",
                        snapshot.tps1m(), snapshot.tps5m(), snapshot.tps15m())),
                avgTps5m.isPresent()
                        ? text("performance.gui.home.tps-history-avg", String.format(Locale.ROOT, "%.2f", avgTps5m.getAsDouble()))
                        : Component.empty())));
        view.place(slot++, GuiItem.of(Material.CLOCK, text("performance.gui.home.mspt"), List.of(
                Component.text(String.format(Locale.ROOT, "%.2f ms", snapshot.mspt())))));
        view.place(slot++, GuiItem.of(Material.REDSTONE, text("performance.gui.home.memory"), List.of(
                Component.text(formatMegabytes(snapshot.usedMemoryBytes()) + " MB / " + formatMegabytes(snapshot.maxMemoryBytes()) + " MB"
                        + String.format(Locale.ROOT, " (%.1f%%)", snapshot.usedMemoryPercent())))));
        view.place(slot++, GuiItem.of(Material.PLAYER_HEAD, text("performance.gui.home.players"), List.of(
                Component.text(String.valueOf(snapshot.onlinePlayers())))));
        view.place(slot++, GuiItem.of(Material.CHEST, text("performance.gui.home.chunks"), List.of(
                Component.text(String.valueOf(snapshot.loadedChunks())))));
        view.place(slot++, GuiItem.of(Material.ZOMBIE_HEAD, text("performance.gui.home.entities"), List.of(
                Component.text(String.valueOf(snapshot.entityCount())))));
        view.place(slot++, GuiItem.of(Material.GRASS_BLOCK, text("performance.gui.home.worlds"), List.of(
                Component.text(String.valueOf(snapshot.worldCount())))));
        view.place(slot, GuiItem.of(Material.RECOVERY_COMPASS, text("performance.gui.home.uptime"), List.of(
                Component.text(formatUptime(snapshot.uptime())))));
    }

    private void renderNavigation(GuiView view, Player viewer) {
        view.place(GuiLayout.contentSlot(GuiLayout.COLUMNS),
                GuiButton.of(GuiItem.of(Material.FILLED_MAP, text("performance.gui.home.worlds-nav"),
                        List.of(text("performance.gui.home.worlds-nav-lore"))), clickCtx -> clickCtx.open(new PerformanceWorldsPage(ctx))),
                viewer);
        view.place(GuiLayout.contentSlot(GuiLayout.COLUMNS + 1),
                GuiButton.of(GuiItem.of(Material.SPAWNER, text("performance.gui.home.entities-nav"),
                        List.of(text("performance.gui.home.entities-nav-lore"))), clickCtx -> clickCtx.open(new PerformanceEntityOverviewPage(ctx))),
                viewer);
    }

    private String formatUptime(Duration uptime) {
        long seconds = uptime.toSeconds();
        return "%dh %dm %ds".formatted(seconds / 3600, (seconds % 3600) / 60, seconds % 60);
    }

    private String formatMegabytes(long bytes) {
        return String.valueOf(bytes / (1024 * 1024));
    }
}

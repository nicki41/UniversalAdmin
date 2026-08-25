package dev.universaladmin.modules.performance.gui;

import dev.universaladmin.gui.AbstractGuiPage;
import dev.universaladmin.gui.ConfirmationDialog;
import dev.universaladmin.gui.GuiButton;
import dev.universaladmin.gui.GuiItem;
import dev.universaladmin.gui.GuiLayout;
import dev.universaladmin.gui.GuiPageId;
import dev.universaladmin.gui.GuiRenderContext;
import dev.universaladmin.gui.GuiView;
import dev.universaladmin.modules.performance.EntityOverviewSnapshot;
import dev.universaladmin.modules.performance.PerformancePermissions;
import dev.universaladmin.modules.performance.action.ClearEntitiesAction;
import dev.universaladmin.modules.performance.action.ClearEntitiesInput;
import java.util.EnumSet;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

/**
 * {@code performance.entities} - a small menu, not a list: the total entity
 * count plus navigation into "by type" (paginated) and "by world" (reuses
 * {@link PerformanceWorldsPage} - same underlying per-world numbers, see
 * that class), plus a single, dangerous "clear everything not protected"
 * shortcut for {@link PerformancePermissions#ENTITY_CLEAR} holders. Per-type
 * clearing lives on {@link PerformanceEntityByTypePage} instead, where a
 * specific type (and its live preview count) is right there to confirm
 * against.
 */
public final class PerformanceEntityOverviewPage extends AbstractGuiPage {

    public static final GuiPageId ID = GuiPageId.core("performance.entities");

    private final PerformanceGuiContext ctx;

    public PerformanceEntityOverviewPage(PerformanceGuiContext ctx) {
        super(ID, ctx.framework(), ctx.messages());
        this.ctx = ctx;
    }

    @Override
    protected Component title(Player viewer) {
        return text("performance.gui.entities.title");
    }

    @Override
    protected void renderContent(GuiRenderContext context) {
        GuiView view = context.view();
        Player viewer = context.viewer();
        EntityOverviewSnapshot overview = ctx.samplingService().entityOverview();

        view.place(GuiLayout.CONTENT_START_SLOT, GuiItem.of(Material.SPAWNER, text("performance.gui.entities.total"),
                List.of(Component.text(String.valueOf(overview.totalEntities())))));

        view.place(GuiLayout.contentSlot(GuiLayout.COLUMNS),
                GuiButton.of(GuiItem.of(Material.BOOK, text("performance.gui.entities.by-type"),
                        List.of(text("performance.gui.entities.by-type-lore"))), clickCtx -> clickCtx.open(new PerformanceEntityByTypePage(ctx))),
                viewer);
        view.place(GuiLayout.contentSlot(GuiLayout.COLUMNS + 1),
                GuiButton.of(GuiItem.of(Material.MAP, text("performance.gui.entities.by-world"),
                        List.of(text("performance.gui.entities.by-world-lore"))), clickCtx -> clickCtx.open(new PerformanceWorldsPage(ctx))),
                viewer);

        view.place(GuiLayout.contentSlot(2 * GuiLayout.COLUMNS),
                new GuiButton(GuiItem.of(Material.LAVA_BUCKET, text("performance.gui.entities.clear-all"),
                        List.of(text("performance.gui.entities.clear-all-lore"))),
                        PerformancePermissions.ENTITY_CLEAR, clickCtx -> promptClearAll(clickCtx.viewer())),
                viewer);
    }

    private void promptClearAll(Player viewer) {
        int preview = ctx.samplingService().previewClearCount(EnumSet.allOf(EntityType.class), null);
        if (preview == 0) {
            viewer.sendMessage(text("performance.gui.entities.nothing-to-clear"));
            return;
        }
        ConfirmationDialog.open(viewer, framework, messages, text("performance.gui.entities.clear-all"),
                List.of(text("performance.gui.entities.clear-all-confirm", preview)),
                ConfirmationDialog.DangerLevel.DANGEROUS,
                confirmCtx -> PerformanceGuiActions.<ClearEntitiesInput>runAction(ctx, viewer, ClearEntitiesAction.ID,
                        new ClearEntitiesInput(EnumSet.allOf(EntityType.class), null), () -> this.open(viewer)),
                confirmCtx -> this.open(viewer));
    }
}

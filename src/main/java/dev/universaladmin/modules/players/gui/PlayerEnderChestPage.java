package dev.universaladmin.modules.players.gui;

import dev.universaladmin.gui.AbstractGuiPage;
import dev.universaladmin.gui.GuiButton;
import dev.universaladmin.gui.GuiItem;
import dev.universaladmin.gui.GuiLayout;
import dev.universaladmin.gui.GuiPageId;
import dev.universaladmin.gui.GuiRenderContext;
import dev.universaladmin.gui.GuiView;
import dev.universaladmin.modules.players.PlayerPermissions;
import dev.universaladmin.modules.players.inventory.LiveInventoryKind;
import dev.universaladmin.modules.players.inventory.LiveInventorySlot;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * A player's ender chest, live in both directions - same mechanism as
 * {@link PlayerInventoryPage}, gated on {@code players.enderchest.*}.
 */
public final class PlayerEnderChestPage extends AbstractGuiPage {

    public static final GuiPageId ID = GuiPageId.core("players.enderchest");

    private final PlayerGuiContext ctx;
    private final UUID targetId;
    private final String targetName;

    public PlayerEnderChestPage(PlayerGuiContext ctx, UUID targetId, String targetName) {
        super(ID, ctx.framework(), ctx.messages());
        this.ctx = ctx;
        this.targetId = targetId;
        this.targetName = targetName;
    }

    @Override
    protected boolean refreshable() {
        return false;
    }

    @Override
    protected Component title(Player viewer) {
        return text(viewer.hasPermission(PlayerPermissions.ENDERCHEST_EDIT.value())
                ? "players.gui.enderchest.title" : "players.gui.enderchest.title-view-only", targetName);
    }

    @Override
    protected void renderContent(GuiRenderContext context) {
        GuiView view = context.view();
        Player viewer = context.viewer();
        Player target = Bukkit.getPlayer(targetId);
        if (target == null) {
            view.place(GuiLayout.contentSlot(GuiLayout.contentSize() / 2), GuiItem.of(framework.icons().empty(), text("players.action.offline")));
            return;
        }

        boolean canEdit = viewer.hasPermission(PlayerPermissions.ENDERCHEST_EDIT.value());
        view.editable(canEdit);
        view.place(LiveInventoryLayout.LEGEND_SLOT, GuiButton.of(GuiItem.of(Material.ENDER_EYE, text("players.gui.enderchest.legend"),
                List.of(text(canEdit ? "players.gui.inventory.legend-live-edit" : "players.gui.inventory.legend-live-view"))),
                clickCtx -> { }), viewer);

        List<LiveInventorySlot> slots = LiveInventoryLayout.enderChest();
        LiveInventoryLayout.lockFreeSlots(view, viewer, slots);
        ctx.liveInventory().start(view, viewer, target, LiveInventoryKind.ENDER_CHEST, canEdit, slots);
    }
}

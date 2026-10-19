package dev.universaladmin.modules.players.gui;

import dev.universaladmin.gui.AbstractGuiPage;
import dev.universaladmin.gui.ConfirmationDialog;
import dev.universaladmin.gui.GuiActions;
import dev.universaladmin.gui.GuiButton;
import dev.universaladmin.gui.GuiClickContext;
import dev.universaladmin.gui.GuiItem;
import dev.universaladmin.gui.GuiLayout;
import dev.universaladmin.gui.GuiPageId;
import dev.universaladmin.gui.GuiRenderContext;
import dev.universaladmin.gui.GuiView;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.modules.players.PlayerPermissions;
import dev.universaladmin.modules.players.action.PlayerActionIds;
import dev.universaladmin.modules.players.action.PlayerTargetInput;
import dev.universaladmin.modules.players.inventory.LiveInventoryKind;
import dev.universaladmin.modules.players.inventory.LiveInventorySlot;
import dev.universaladmin.settings.CoreSettings;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * A player's inventory - storage, hotbar, armor and offhand - live: every
 * change the player makes shows up here within a tick, and with {@code
 * players.inventory.edit} every change made here lands in the player's
 * inventory within a tick ({@link dev.universaladmin.modules.players.inventory.LiveInventoryService}).
 * Without the edit permission it is view-only and nothing can be moved.
 * See docs/user/modules/players.md.
 */
public final class PlayerInventoryPage extends AbstractGuiPage {

    public static final GuiPageId ID = GuiPageId.core("players.inventory");

    private final PlayerGuiContext ctx;
    private final UUID targetId;
    private final String targetName;

    public PlayerInventoryPage(PlayerGuiContext ctx, UUID targetId, String targetName) {
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
        return text(viewer.hasPermission(PlayerPermissions.INVENTORY_EDIT.value())
                ? "players.gui.inventory.title" : "players.gui.inventory.title-view-only", targetName);
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

        boolean canEdit = viewer.hasPermission(PlayerPermissions.INVENTORY_EDIT.value());
        view.editable(canEdit);
        if (canEdit) {
            view.place(LiveInventoryLayout.CLEAR_SLOT, GuiButton.of(GuiItem.of(Material.TNT, text("players.gui.inventory.clear"),
                    List.of(text("players.gui.inventory.clear-lore"))), this::confirmClear), viewer);
        }
        view.place(LiveInventoryLayout.LEGEND_SLOT, GuiButton.of(GuiItem.of(Material.BOOK, text("players.gui.inventory.legend"),
                List.of(text("players.gui.inventory.legend-storage"), text("players.gui.inventory.legend-hotbar"),
                        text("players.gui.inventory.legend-equipment"),
                        text(canEdit ? "players.gui.inventory.legend-live-edit" : "players.gui.inventory.legend-live-view"))),
                clickCtx -> { }), viewer);

        List<LiveInventorySlot> slots = LiveInventoryLayout.inventory();
        LiveInventoryLayout.lockFreeSlots(view, viewer, slots);
        ctx.liveInventory().start(view, viewer, target, LiveInventoryKind.INVENTORY, canEdit, slots);
    }

    private void confirmClear(GuiClickContext clickCtx) {
        Player viewer = clickCtx.viewer();
        if (!ctx.settings().get(CoreSettings.GUI_CONFIRMATIONS)) {
            clear(viewer);
            return;
        }
        ConfirmationDialog.open(viewer, framework, messages,
                text("players.gui.inventory.clear-confirm-title"),
                List.of(text("players.gui.inventory.clear-confirm-body", targetName)),
                ConfirmationDialog.DangerLevel.DANGEROUS,
                confirmCtx -> clear(confirmCtx.viewer()),
                GuiClickContext::back);
    }

    private void clear(Player viewer) {
        GuiActions.runAndReport(ctx.actionExecutor(), ctx.scheduler(), messages, viewer,
                PlayerActionIds.INVENTORY_CLEAR, new PlayerTargetInput(targetId), MessageKey.of("players.gui.action.success"), () -> { });
    }
}

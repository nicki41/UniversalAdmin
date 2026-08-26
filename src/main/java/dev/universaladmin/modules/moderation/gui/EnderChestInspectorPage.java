package dev.universaladmin.modules.moderation.gui;

import dev.universaladmin.action.ActionId;
import dev.universaladmin.gui.AbstractGuiPage;
import dev.universaladmin.gui.GuiButton;
import dev.universaladmin.gui.GuiItem;
import dev.universaladmin.gui.GuiLayout;
import dev.universaladmin.gui.GuiPageId;
import dev.universaladmin.gui.GuiRenderContext;
import dev.universaladmin.gui.GuiView;
import dev.universaladmin.modules.players.PlayerPermissions;
import dev.universaladmin.modules.players.action.PlayerActionIds;
import dev.universaladmin.modules.players.action.SetEnderChestContentsInput;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * The Staff-Mode Ender Chest Inspector tool - a live mirror of a target's
 * 27-slot ender chest, occupying the first 27 of the 36 content slots (the
 * remaining 9 are locked filler), same shape as {@code PlayerEnderChestPage}
 * in the Players module. View-only ({@code editable(false)}) without {@link
 * PlayerPermissions#ENDERCHEST_EDIT}; with it, every click/drag mirrors onto
 * the real target immediately via {@link GuiView#onChange}. Ephemeral, built
 * fresh per click, never registered in {@code GuiRegistry}.
 */
public final class EnderChestInspectorPage extends AbstractGuiPage {

    public static final GuiPageId ID = GuiPageId.core("moderation.enderchest-inspector");

    private static final int ENDER_CHEST_SIZE = 27;

    private final ModerationGuiContext ctx;
    private final UUID targetId;
    private final String targetName;

    public EnderChestInspectorPage(ModerationGuiContext ctx, UUID targetId, String targetName) {
        super(ID, ctx.framework(), ctx.messages());
        this.ctx = ctx;
        this.targetId = targetId;
        this.targetName = targetName;
    }

    @Override
    protected boolean refreshable() {
        return true;
    }

    @Override
    protected Component title(Player viewer) {
        return text("moderation.gui.enderchest-inspector.title", targetName);
    }

    @Override
    protected void renderContent(GuiRenderContext context) {
        GuiView view = context.view();
        Player viewer = context.viewer();
        Player target = Bukkit.getPlayer(targetId);
        if (target == null) {
            view.place(GuiLayout.contentSlot(GuiLayout.contentSize() / 2), GuiItem.of(framework.icons().empty(), text("moderation.action.offline")));
            return;
        }

        boolean canEdit = viewer.hasPermission(PlayerPermissions.ENDERCHEST_EDIT.value());
        view.editable(canEdit);

        ItemStack[] contents = target.getEnderChest().getContents();
        for (int i = 0; i < contents.length && i < ENDER_CHEST_SIZE; i++) {
            if (contents[i] != null) {
                view.place(GuiLayout.contentSlot(i), new GuiItem(contents[i]));
            }
        }
        GuiItem filler = GuiItem.filler(Material.GRAY_STAINED_GLASS_PANE);
        for (int i = ENDER_CHEST_SIZE; i < GuiLayout.contentSize(); i++) {
            view.place(GuiLayout.contentSlot(i), GuiButton.of(filler, clickCtx -> { }), viewer);
        }

        if (canEdit) {
            // Live, silent on success - see InventoryInspectorPage#persist.
            view.onChange(view2 -> persist(view2, false));
            view.onClose(view2 -> persist(view2, true));
        }
    }

    private void persist(GuiView view, boolean notify) {
        Player staff = Bukkit.getPlayer(view.viewerId());
        if (staff == null) {
            return;
        }
        List<ItemStack> contents = new ArrayList<>();
        for (int i = 0; i < ENDER_CHEST_SIZE; i++) {
            contents.add(view.getInventory().getItem(GuiLayout.contentSlot(i)));
        }
        ActionId id = PlayerActionIds.ENDERCHEST_SET;
        SetEnderChestContentsInput input = new SetEnderChestContentsInput(targetId, contents);
        ctx.actionExecutor().<SetEnderChestContentsInput, Object>execute(id, ModerationGuiActions.contextFor(staff), input)
                .whenComplete((result, error) -> ctx.scheduler().runOnMainThread(() -> {
                    if (!staff.isOnline()) {
                        return;
                    }
                    if (error != null) {
                        ModerationGuiActions.notifyError(staff, ctx.messages());
                    } else if (notify) {
                        ModerationGuiActions.notifyResult(staff, ctx.messages(), id, result);
                    }
                }));
    }
}

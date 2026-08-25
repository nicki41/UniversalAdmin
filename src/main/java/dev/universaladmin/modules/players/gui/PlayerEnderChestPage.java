package dev.universaladmin.modules.players.gui;

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
 * The 27-slot ender chest, occupying the first 27 of the 36 content slots -
 * the remaining 9 are locked filler. Same view/edit/live-persist shape as
 * {@link PlayerInventoryPage} (no Save button - {@link GuiView#onClose}
 * persists on close), gated on {@code players.enderchest.*} instead.
 */
public final class PlayerEnderChestPage extends AbstractGuiPage {

    public static final GuiPageId ID = GuiPageId.core("players.enderchest");

    private static final int ENDER_CHEST_SIZE = 27;

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
    protected Component title(Player viewer) {
        return text("players.gui.enderchest.title", targetName);
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
            view.onClose(this::persistOnClose);
        }
    }

    /** Runs once the admin actually closes this view - see {@link PlayerInventoryPage#persistOnClose} for why. */
    private void persistOnClose(GuiView view) {
        Player admin = Bukkit.getPlayer(view.viewerId());
        if (admin == null) {
            return;
        }
        List<ItemStack> contents = new ArrayList<>();
        for (int i = 0; i < ENDER_CHEST_SIZE; i++) {
            contents.add(view.getInventory().getItem(GuiLayout.contentSlot(i)));
        }
        runAction(admin, PlayerActionIds.ENDERCHEST_SET, new SetEnderChestContentsInput(targetId, contents));
    }

    private <I> void runAction(Player viewer, ActionId id, I input) {
        ctx.actionExecutor().<I, Object>execute(id, PlayerGuiActions.contextFor(viewer), input)
                .whenComplete((result, error) -> ctx.scheduler().runOnMainThread(() -> {
                    if (!viewer.isOnline()) {
                        return;
                    }
                    if (error != null) {
                        PlayerGuiActions.notifyError(viewer, messages);
                    } else {
                        PlayerGuiActions.notifyResult(viewer, messages, result);
                    }
                }));
    }
}

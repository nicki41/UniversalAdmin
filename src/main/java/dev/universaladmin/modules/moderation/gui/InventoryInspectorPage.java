package dev.universaladmin.modules.moderation.gui;

import dev.universaladmin.action.ActionId;
import dev.universaladmin.gui.AbstractGuiPage;
import dev.universaladmin.gui.EmptySlotPlaceholder;
import dev.universaladmin.gui.GuiButton;
import dev.universaladmin.gui.GuiItem;
import dev.universaladmin.gui.GuiLayout;
import dev.universaladmin.gui.GuiPageId;
import dev.universaladmin.gui.GuiRenderContext;
import dev.universaladmin.gui.GuiView;
import dev.universaladmin.modules.players.PlayerPermissions;
import dev.universaladmin.modules.players.action.InventorySection;
import dev.universaladmin.modules.players.action.PlayerActionIds;
import dev.universaladmin.modules.players.action.SetInventoryContentsInput;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * The Staff-Mode Inventory Inspector tool - a live mirror of a target's main
 * storage (36 slots, 1:1 with the content area) plus armor/offhand (fixed
 * bottom-row slots, same layout as {@code PlayerInventoryPage} in the
 * Players module). View-only ({@code editable(false)}) without {@link
 * PlayerPermissions#INVENTORY_EDIT}; with it, every click/drag mirrors onto
 * the real target immediately via {@link GuiView#onChange} - see that
 * method's javadoc - the same live-persist shape {@code PlayerInventoryPage}
 * uses, just reached from the staff-mode tool instead of the Players module
 * GUI. Ephemeral, built fresh per click, never registered in {@code
 * GuiRegistry}.
 */
public final class InventoryInspectorPage extends AbstractGuiPage {

    public static final GuiPageId ID = GuiPageId.core("moderation.inventory-inspector");

    /** Main storage's own first 9 slots are the hotbar - see {@link #renderContent}. */
    private static final int HOTBAR_SIZE = 9;
    private static final int HELMET_SLOT = 46;
    private static final int CHEST_SLOT = 47;
    private static final int LEGS_SLOT = 48;
    private static final int BOOTS_SLOT = 49;
    private static final int OFFHAND_SLOT = 50;

    private final ModerationGuiContext ctx;
    private final UUID targetId;
    private final String targetName;

    public InventoryInspectorPage(ModerationGuiContext ctx, UUID targetId, String targetName) {
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
        return text("moderation.gui.inventory-inspector.title", targetName);
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

        boolean canEdit = viewer.hasPermission(PlayerPermissions.INVENTORY_EDIT.value());
        view.editable(canEdit);

        ItemStack[] contents = target.getInventory().getStorageContents();
        for (int i = 0; i < contents.length && i < GuiLayout.contentSize(); i++) {
            if (contents[i] != null) {
                view.place(GuiLayout.contentSlot(i), new GuiItem(contents[i]));
            } else {
                view.place(GuiLayout.contentSlot(i), i < HOTBAR_SIZE
                        ? EmptySlotPlaceholder.hotbar(text("gui.inventory-placeholder.hotbar"))
                        : EmptySlotPlaceholder.generic(text("gui.inventory-placeholder.main")));
            }
        }

        PlayerInventory inventory = target.getInventory();
        placeEquipmentSlot(view, HELMET_SLOT, inventory.getHelmet(), Material.LEATHER_HELMET, "gui.inventory-placeholder.helmet");
        placeEquipmentSlot(view, CHEST_SLOT, inventory.getChestplate(), Material.LEATHER_CHESTPLATE, "gui.inventory-placeholder.chestplate");
        placeEquipmentSlot(view, LEGS_SLOT, inventory.getLeggings(), Material.LEATHER_LEGGINGS, "gui.inventory-placeholder.leggings");
        placeEquipmentSlot(view, BOOTS_SLOT, inventory.getBoots(), Material.LEATHER_BOOTS, "gui.inventory-placeholder.boots");
        placeOffhandSlot(view, inventory.getItemInOffHand());

        // Pure visual separation between storage and equipment, same as
        // PlayerInventoryPage's FILLER_SLOTS - never a place to stash an item.
        GuiItem filler = GuiItem.filler(Material.GRAY_STAINED_GLASS_PANE);
        for (int slot = 51; slot <= 53; slot++) {
            view.place(slot, GuiButton.of(filler, clickCtx -> { }), viewer);
        }

        if (canEdit) {
            // Live: mirrors onto the real target after every click/drag,
            // silently (a chat message per item moved would spam the
            // staff member) - see PlayerInventoryPage#persist for the
            // identical reasoning.
            view.onChange(view2 -> persist(view2, false));
            view.onClose(view2 -> persist(view2, true));
        }
    }

    private void placeEquipmentSlot(GuiView view, int slot, ItemStack item, Material placeholderPiece, String placeholderLabelKey) {
        view.place(slot, item != null ? new GuiItem(item) : EmptySlotPlaceholder.armor(placeholderPiece, text(placeholderLabelKey)));
    }

    private void placeOffhandSlot(GuiView view, ItemStack item) {
        view.place(OFFHAND_SLOT, item != null ? new GuiItem(item) : EmptySlotPlaceholder.offhand(text("gui.inventory-placeholder.offhand")));
    }

    private void persist(GuiView view, boolean notify) {
        Player staff = Bukkit.getPlayer(view.viewerId());
        if (staff == null) {
            return;
        }
        // Every empty slot currently holds a decorative EmptySlotPlaceholder
        // (see renderContent) - stripped back to null here, otherwise a fake
        // "Empty - Helmet" item would get written straight into the target's
        // real inventory.
        List<ItemStack> storage = new ArrayList<>();
        for (int i = 0; i < GuiLayout.contentSize(); i++) {
            storage.add(view.getInventory().getItem(GuiLayout.contentSlot(i)));
        }
        storage = EmptySlotPlaceholder.stripPlaceholders(storage);
        List<ItemStack> equipment = new ArrayList<>();
        equipment.add(view.getInventory().getItem(HELMET_SLOT));
        equipment.add(view.getInventory().getItem(CHEST_SLOT));
        equipment.add(view.getInventory().getItem(LEGS_SLOT));
        equipment.add(view.getInventory().getItem(BOOTS_SLOT));
        equipment.add(view.getInventory().getItem(OFFHAND_SLOT));
        equipment = EmptySlotPlaceholder.stripPlaceholders(equipment);

        // Two separate ActionExecutor calls (MAIN/EQUIPMENT stay independently
        // audited) but one notification: only the first is allowed to notify,
        // so a close/live-sync never shows the same confirmation twice.
        runAction(staff, new SetInventoryContentsInput(targetId, InventorySection.MAIN, storage), notify);
        runAction(staff, new SetInventoryContentsInput(targetId, InventorySection.EQUIPMENT, equipment), false);
    }

    private void runAction(Player staff, SetInventoryContentsInput input, boolean notify) {
        ActionId id = PlayerActionIds.INVENTORY_SET;
        ctx.actionExecutor().<SetInventoryContentsInput, Object>execute(id, ModerationGuiActions.contextFor(staff), input)
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

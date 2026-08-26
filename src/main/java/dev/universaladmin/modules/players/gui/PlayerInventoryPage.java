package dev.universaladmin.modules.players.gui;

import dev.universaladmin.action.ActionId;
import dev.universaladmin.gui.AbstractGuiPage;
import dev.universaladmin.gui.ConfirmationDialog;
import dev.universaladmin.gui.EmptySlotPlaceholder;
import dev.universaladmin.gui.GuiButton;
import dev.universaladmin.gui.GuiClickContext;
import dev.universaladmin.gui.GuiItem;
import dev.universaladmin.gui.GuiLayout;
import dev.universaladmin.gui.GuiPageId;
import dev.universaladmin.gui.GuiRenderContext;
import dev.universaladmin.gui.GuiView;
import dev.universaladmin.modules.players.PlayerPermissions;
import dev.universaladmin.modules.players.action.InventorySection;
import dev.universaladmin.modules.players.action.PlayerActionIds;
import dev.universaladmin.modules.players.action.PlayerTargetInput;
import dev.universaladmin.modules.players.action.SetInventoryContentsInput;
import dev.universaladmin.settings.CoreSettings;
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
 * Main storage (36 slots, 1:1 with the content area) plus armor/offhand (row
 * 5) in one view - view-only ({@code editable(false)}, nothing can move)
 * with only {@code players.inventory.view}, or freely editable with {@code
 * players.inventory.edit}. Editing is live: there is no Save button - {@link
 * GuiView#onChange} mirrors the view's current contents onto the real
 * target's inventory after every click/drag, and {@link GuiView#onClose}
 * does the same once more as a final flush when the admin actually closes
 * the view (walks away, hits Esc, or uses Back/Close). See
 * docs/user/modules/players.md.
 */
public final class PlayerInventoryPage extends AbstractGuiPage {

    public static final GuiPageId ID = GuiPageId.core("players.inventory");

    /** Main storage's own first 9 slots are the hotbar - see {@link #renderContent}. */
    private static final int HOTBAR_SIZE = 9;
    private static final int CLEAR_SLOT = 45;
    private static final int HELMET_SLOT = 46;
    private static final int CHEST_SLOT = 47;
    private static final int LEGS_SLOT = 48;
    private static final int BOOTS_SLOT = 49;
    private static final int OFFHAND_SLOT = 50;
    private static final int[] FILLER_SLOTS = {51, 52, 53};

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
    protected Component title(Player viewer) {
        return text("players.gui.inventory.title", targetName);
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

        // Locked even in an editable view - a GuiButton's slot always is
        // (see GuiListener), which is exactly the point: these three slots
        // are pure visual separation between storage and equipment, never a
        // place to stash an item.
        GuiItem filler = GuiItem.filler(Material.GRAY_STAINED_GLASS_PANE);
        for (int slot : FILLER_SLOTS) {
            view.place(slot, GuiButton.of(filler, clickCtx -> { }), viewer);
        }

        if (canEdit) {
            view.place(CLEAR_SLOT, GuiButton.of(GuiItem.of(Material.TNT, text("players.gui.inventory.clear")),
                    this::confirmClear), viewer);
            // Live: mirrors onto the real target after every click/drag, not
            // only once at close - see GuiView#onChange. Silent on success
            // (a chat message per item moved would spam the admin); onClose
            // still notifies once, as the familiar "final save" confirmation.
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

    /**
     * Mirrors the view's current contents onto the real target - called
     * after every click/drag ({@code notify = false}) and once more on
     * close ({@code notify = true}, the familiar "final save" confirmation).
     * Re-resolves the target fresh (never captures the {@code Player} from
     * render time, see docs/development/gui-framework.md's "Player Session"
     * section) since the admin may have left this open for a while; if the
     * target went offline in the meantime, {@code SetPlayerInventoryContentsAction}
     * itself reports that failure the normal way, nothing special-cased here.
     */
    private void persist(GuiView view, boolean notify) {
        Player admin = Bukkit.getPlayer(view.viewerId());
        if (admin == null) {
            return;
        }
        // Every empty slot currently holds a decorative EmptySlotPlaceholder
        // (see renderContent) rather than being genuinely empty in the GuiView's
        // backing Inventory - stripped back to null here, otherwise a fake
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
        runAction(admin, PlayerActionIds.INVENTORY_SET, new SetInventoryContentsInput(targetId, InventorySection.MAIN, storage), notify);
        runAction(admin, PlayerActionIds.INVENTORY_SET, new SetInventoryContentsInput(targetId, InventorySection.EQUIPMENT, equipment), false);
    }

    private void confirmClear(GuiClickContext clickCtx) {
        Player viewer = clickCtx.viewer();
        if (!ctx.settings().get(CoreSettings.GUI_CONFIRMATIONS)) {
            runAction(viewer, PlayerActionIds.INVENTORY_CLEAR, new PlayerTargetInput(targetId), true);
            return;
        }
        ConfirmationDialog.open(viewer, framework, messages,
                text("players.gui.inventory.clear-confirm-title"),
                List.of(text("players.gui.inventory.clear-confirm-body")),
                ConfirmationDialog.DangerLevel.DANGEROUS,
                confirmCtx -> runAction(confirmCtx.viewer(), PlayerActionIds.INVENTORY_CLEAR, new PlayerTargetInput(targetId), true),
                GuiClickContext::back);
    }

    /** @param notify whether to tell {@code viewer} the outcome - {@code false} for a silent live-sync write, {@code true} for a deliberate one-off action. */
    private <I> void runAction(Player viewer, ActionId id, I input, boolean notify) {
        ctx.actionExecutor().<I, Object>execute(id, PlayerGuiActions.contextFor(viewer), input)
                .whenComplete((result, error) -> ctx.scheduler().runOnMainThread(() -> {
                    if (!viewer.isOnline()) {
                        return;
                    }
                    if (error != null) {
                        PlayerGuiActions.notifyError(viewer, messages);
                    } else if (notify) {
                        PlayerGuiActions.notifyResult(viewer, messages, result);
                    }
                }));
    }
}

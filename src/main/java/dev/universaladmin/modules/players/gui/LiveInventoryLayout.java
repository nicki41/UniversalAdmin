package dev.universaladmin.modules.players.gui;

import dev.universaladmin.gui.GuiButton;
import dev.universaladmin.gui.GuiItem;
import dev.universaladmin.gui.GuiLayout;
import dev.universaladmin.gui.GuiView;
import dev.universaladmin.modules.players.inventory.LiveInventorySlot;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * Where the live editors put which slot. The inventory editor follows the
 * player's own screen: main storage in the top three content rows, the
 * hotbar below it, then helmet, chestplate, leggings, boots and offhand.
 */
final class LiveInventoryLayout {

    static final int CLEAR_SLOT = 45;
    static final int LEGEND_SLOT = 51;

    private static final int HOTBAR_ROW_START = GuiLayout.CONTENT_START_SLOT + 27;
    private static final int ENDER_CHEST_SIZE = 27;

    private LiveInventoryLayout() {
    }

    /** {@code PlayerInventory} indices: 0-8 hotbar, 9-35 storage, 36 boots, 37 leggings, 38 chestplate, 39 helmet, 40 offhand. */
    static List<LiveInventorySlot> inventory() {
        List<LiveInventorySlot> slots = new ArrayList<>();
        for (int index = 9; index <= 35; index++) {
            slots.add(new LiveInventorySlot(index, index));
        }
        for (int index = 0; index <= 8; index++) {
            slots.add(new LiveInventorySlot(HOTBAR_ROW_START + index, index));
        }
        slots.add(new LiveInventorySlot(46, 39));
        slots.add(new LiveInventorySlot(47, 38));
        slots.add(new LiveInventorySlot(48, 37));
        slots.add(new LiveInventorySlot(49, 36));
        slots.add(new LiveInventorySlot(50, 40));
        return slots;
    }

    /** The 27 ender chest slots in the top three content rows. */
    static List<LiveInventorySlot> enderChest() {
        List<LiveInventorySlot> slots = new ArrayList<>();
        for (int index = 0; index < ENDER_CHEST_SIZE; index++) {
            slots.add(new LiveInventorySlot(GuiLayout.contentSlot(index), index));
        }
        return slots;
    }

    /**
     * Fills every slot that is neither linked nor already used with a locked
     * pane. In an editable view any free slot would accept items - which
     * would then sit in the menu instead of the player's inventory and be
     * gone when it closes.
     */
    static void lockFreeSlots(GuiView view, Player viewer, List<LiveInventorySlot> linked) {
        Set<Integer> linkedSlots = linked.stream().map(LiveInventorySlot::viewSlot).collect(Collectors.toSet());
        GuiItem filler = GuiItem.filler(Material.GRAY_STAINED_GLASS_PANE);
        for (int slot = 0; slot < view.getInventory().getSize(); slot++) {
            if (!linkedSlots.contains(slot) && view.getInventory().getItem(slot) == null) {
                view.place(slot, GuiButton.of(filler, clickCtx -> { }), viewer);
            }
        }
    }
}

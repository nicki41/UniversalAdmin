package dev.universaladmin.modules.players.inventory;

import dev.universaladmin.core.PlayerNames;
import dev.universaladmin.gui.GuiView;
import dev.universaladmin.modules.players.action.InventoryChangeSummary;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * One open live editor: a {@link GuiView} whose linked slots are kept equal
 * to a player's real inventory or ender chest, in both directions, once per
 * tick (see {@link SlotSync}). Main thread only - every method touches
 * Bukkit inventories.
 */
final class LiveInventorySession {

    private final GuiView view;
    private final UUID viewerId;
    private final UUID targetId;
    private final String targetName;
    private final LiveInventoryKind kind;
    private final boolean editable;
    private final List<LiveInventorySlot> slots;
    private final ItemStack[] initial;
    private final ItemStack[] last;
    private final Set<Integer> changedByViewer = new HashSet<>();

    LiveInventorySession(GuiView view, UUID viewerId, Player target, LiveInventoryKind kind, boolean editable, List<LiveInventorySlot> slots) {
        this.view = view;
        this.viewerId = viewerId;
        this.targetId = target.getUniqueId();
        this.targetName = PlayerNames.real(target);
        this.kind = kind;
        this.editable = editable;
        this.slots = List.copyOf(slots);
        this.initial = new ItemStack[slots.size()];
        this.last = new ItemStack[slots.size()];
        Inventory source = kind.inventoryOf(target);
        for (int i = 0; i < this.slots.size(); i++) {
            ItemStack item = copy(source.getItem(this.slots.get(i).targetIndex()));
            initial[i] = item;
            last[i] = item;
            view.getInventory().setItem(this.slots.get(i).viewSlot(), copy(item));
        }
    }

    GuiView view() {
        return view;
    }

    UUID viewerId() {
        return viewerId;
    }

    UUID targetId() {
        return targetId;
    }

    String targetName() {
        return targetName;
    }

    LiveInventoryKind kind() {
        return kind;
    }

    boolean editable() {
        return editable;
    }

    /** Copies whatever changed since the last tick to the other side. */
    void sync(Player target) {
        Inventory real = kind.inventoryOf(target);
        Inventory mirror = view.getInventory();
        for (int i = 0; i < slots.size(); i++) {
            LiveInventorySlot slot = slots.get(i);
            ItemStack shown = copy(mirror.getItem(slot.viewSlot()));
            ItemStack actual = copy(real.getItem(slot.targetIndex()));
            SlotSync decision = SlotSync.decide(shown, actual, last[i], Objects::equals);
            if (decision == SlotSync.TO_TARGET && !editable) {
                // A view-only editor cancels every click, so this only
                // happens if something else wrote into the view - the
                // player's inventory stays the truth either way.
                decision = SlotSync.TO_VIEW;
            }
            switch (decision) {
                case TO_TARGET -> {
                    real.setItem(slot.targetIndex(), copy(shown));
                    last[i] = shown;
                    changedByViewer.add(i);
                }
                case TO_VIEW -> {
                    mirror.setItem(slot.viewSlot(), copy(actual));
                    last[i] = actual;
                }
                case NONE -> {
                    // in sync
                }
            }
        }
    }

    /** Whether the staff member changed at least one slot during this session. */
    boolean changedAnything() {
        return !changedByViewer.isEmpty();
    }

    /** Before/after occupancy plus how many slots the staff member changed - what the audit entry records. */
    InventoryChangeSummary summary() {
        return InventoryChangeSummary.of(initial, last, changedByViewer.size());
    }

    /** {@code null} for an empty slot (Bukkit mixes {@code null} and {@code AIR}); otherwise an independent copy, never a live mirror. */
    private static ItemStack copy(ItemStack item) {
        return item == null || item.getType().isAir() ? null : item.clone();
    }
}

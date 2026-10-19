package dev.universaladmin.modules.players.action;

import java.util.Objects;
import org.bukkit.inventory.ItemStack;

/**
 * Coarse before/after summary for an inventory-contents change, used as the
 * audit {@code oldValue}/{@code newValue} for inventory/ender-chest actions.
 * Deliberately counts only - never the actual items/NBT - per "only as much
 * positional/sensitive data as actually needed" (docs/user/modules/players.md).
 *
 * @param changedSlots how many slots ended up different (for a live editing
 *                     session: how many slots the staff member changed)
 */
public record InventoryChangeSummary(int slots, int nonEmptyBefore, int nonEmptyAfter, int changedSlots) {

    public static InventoryChangeSummary of(ItemStack[] before, ItemStack[] after) {
        int changed = 0;
        for (int i = 0; i < Math.max(before.length, after.length); i++) {
            if (!Objects.equals(normalize(before, i), normalize(after, i))) {
                changed++;
            }
        }
        return of(before, after, changed);
    }

    public static InventoryChangeSummary of(ItemStack[] before, ItemStack[] after, int changedSlots) {
        return new InventoryChangeSummary(before.length, countNonEmpty(before), countNonEmpty(after), changedSlots);
    }

    private static ItemStack normalize(ItemStack[] items, int index) {
        ItemStack item = index < items.length ? items[index] : null;
        return item == null || item.getType().isAir() ? null : item;
    }

    private static int countNonEmpty(ItemStack[] items) {
        int count = 0;
        for (ItemStack item : items) {
            if (item != null && !item.getType().isAir()) {
                count++;
            }
        }
        return count;
    }

    public String describeBefore() {
        return nonEmptyBefore + "/" + slots + " slots occupied";
    }

    public String describeAfter() {
        return nonEmptyAfter + "/" + slots + " slots occupied";
    }
}

package dev.universaladmin.modules.players.action;

import org.bukkit.inventory.ItemStack;

/**
 * Coarse before/after summary for an inventory-contents change, used as the
 * audit {@code oldValue}/{@code newValue} for inventory/ender-chest actions.
 * Deliberately counts only - never the actual items/NBT - per "Positionen
 * und sensible Daten nur soweit nötig" (docs/user/modules/players.md).
 */
public record InventoryChangeSummary(int slots, int nonEmptyBefore, int nonEmptyAfter) {

    public static InventoryChangeSummary of(ItemStack[] before, ItemStack[] after) {
        return new InventoryChangeSummary(before.length, countNonEmpty(before), countNonEmpty(after));
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

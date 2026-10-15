package dev.universaladmin.modules.players.inventory;

/**
 * Links one slot of the editor's view to one slot of the player's real
 * inventory - e.g. editor slot 46 to {@code PlayerInventory} index 39 (the
 * helmet).
 */
public record LiveInventorySlot(int viewSlot, int targetIndex) {
}

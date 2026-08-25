package dev.universaladmin.modules.players.action;

/** Which part of a player's inventory {@code SetPlayerInventoryContentsAction} is writing. */
public enum InventorySection {
    /** The 36 main+hotbar storage slots. */
    MAIN,
    /** Helmet, chestplate, leggings, boots, offhand - in that order. */
    EQUIPMENT
}

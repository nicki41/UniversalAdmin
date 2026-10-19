package dev.universaladmin.modules.players.inventory;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

/** What a live editor shows: the player's inventory (storage, armor, offhand) or their ender chest. */
public enum LiveInventoryKind {

    INVENTORY {
        @Override
        Inventory inventoryOf(Player player) {
            return player.getInventory();
        }
    },
    ENDER_CHEST {
        @Override
        Inventory inventoryOf(Player player) {
            return player.getEnderChest();
        }
    };

    abstract Inventory inventoryOf(Player player);
}

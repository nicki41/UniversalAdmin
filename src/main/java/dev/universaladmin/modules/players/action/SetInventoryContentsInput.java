package dev.universaladmin.modules.players.action;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.bukkit.inventory.ItemStack;

/**
 * @param contents 36 entries (nullable = empty slot) for {@link InventorySection#MAIN},
 *                 or exactly 5 (helmet, chestplate, leggings, boots, offhand) for {@link InventorySection#EQUIPMENT}
 */
public record SetInventoryContentsInput(UUID targetId, InventorySection section, List<ItemStack> contents) {

    // Not List.copyOf: an empty inventory slot is represented as a null
    // element here, which List.copyOf rejects.
    public SetInventoryContentsInput {
        contents = Collections.unmodifiableList(new ArrayList<>(contents));
    }
}

package dev.universaladmin.modules.players.action;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.bukkit.inventory.ItemStack;

/** @param contents exactly 27 entries (nullable = empty slot). */
public record SetEnderChestContentsInput(UUID targetId, List<ItemStack> contents) {

    // Not List.copyOf: an empty slot is represented as a null element here.
    public SetEnderChestContentsInput {
        contents = Collections.unmodifiableList(new ArrayList<>(contents));
    }
}

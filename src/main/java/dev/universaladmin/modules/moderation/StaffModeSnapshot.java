package dev.universaladmin.modules.moderation;

import java.time.Instant;
import java.util.UUID;
import org.bukkit.GameMode;

/**
 * A crash-safe snapshot of everything Staff Mode replaces, taken and
 * persisted <b>before</b> the live inventory is ever touched - see {@code
 * StaffModeService#enter}. {@code inventoryData} is {@code
 * ItemStack.serializeItemsAsBytes(...)} over one combined array (36 storage
 * + 4 armor + 1 offhand slots, in that fixed order - see {@code
 * StaffModeService} for the exact accessor set, same as {@code
 * SetPlayerInventoryContentsAction}/{@code ClearPlayerInventoryAction} in
 * the Players module).
 */
public record StaffModeSnapshot(
        UUID playerId, byte[] inventoryData, GameMode gameMode, float experience, int level,
        boolean allowFlight, boolean flying, Instant createdAt) {
}

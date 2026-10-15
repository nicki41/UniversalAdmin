package dev.universaladmin.modules.players.action;

import java.util.UUID;

/** A finished live editing session of a player's inventory or ender chest - see {@code LiveInventoryService}. */
public record InventoryEditRecord(UUID targetId, String targetName, InventoryChangeSummary summary) {
}

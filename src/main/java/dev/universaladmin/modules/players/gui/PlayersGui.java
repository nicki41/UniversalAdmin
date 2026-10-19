package dev.universaladmin.modules.players.gui;

import dev.universaladmin.core.PlayerRef;
import org.bukkit.entity.Player;

/**
 * Opens the Players module's per-player pages from outside the GUI package -
 * e.g. {@code /admin player <name>} or {@code /admin invsee <name>} - without
 * exposing the package-private {@link PlayerGuiContext}.
 */
public final class PlayersGui {

    private final PlayerGuiContext ctx;

    PlayersGui(PlayerGuiContext ctx) {
        this.ctx = ctx;
    }

    public void openProfile(Player viewer, PlayerRef target) {
        new PlayerProfilePage(ctx, target.id(), target.name()).open(viewer);
    }

    public void openInventory(Player viewer, PlayerRef target) {
        new PlayerInventoryPage(ctx, target.id(), target.name()).open(viewer);
    }

    public void openEnderChest(Player viewer, PlayerRef target) {
        new PlayerEnderChestPage(ctx, target.id(), target.name()).open(viewer);
    }
}

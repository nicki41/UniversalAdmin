package dev.universaladmin.modules.moderation.gui;

import dev.universaladmin.gui.AbstractGuiPage;
import dev.universaladmin.gui.GuiItem;
import dev.universaladmin.gui.GuiLayout;
import dev.universaladmin.gui.GuiPageId;
import dev.universaladmin.gui.GuiRenderContext;
import dev.universaladmin.gui.GuiView;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * Read-only mirror of a live target's inventory (main storage + armor +
 * offhand) - the Inventory Inspector staff tool. View-only by design
 * (every placed slot is a plain {@link GuiItem}, never a {@link
 * dev.universaladmin.gui.GuiButton}): unlike the Players module's own
 * inventory editor, this is purely for looking, not editing - a separate
 * concern from {@code players.inventory.edit}. Ephemeral, built fresh per
 * click, never registered in {@code GuiRegistry}.
 */
public final class InventoryInspectorPage extends AbstractGuiPage {

    public static final GuiPageId ID = GuiPageId.core("moderation.inventory-inspector");

    private final UUID targetId;
    private final String targetName;

    public InventoryInspectorPage(ModerationGuiContext ctx, UUID targetId, String targetName) {
        super(ID, ctx.framework(), ctx.messages());
        this.targetId = targetId;
        this.targetName = targetName;
    }

    @Override
    protected boolean refreshable() {
        return true;
    }

    @Override
    protected Component title(Player viewer) {
        return text("moderation.gui.inventory-inspector.title", targetName);
    }

    @Override
    protected void renderContent(GuiRenderContext context) {
        GuiView view = context.view();
        Player target = Bukkit.getPlayer(targetId);
        if (target == null) {
            view.place(GuiLayout.contentSlot(GuiLayout.contentSize() / 2), GuiItem.of(framework.icons().empty(), text("moderation.action.offline")));
            return;
        }

        // The 36-slot content area exactly fits main storage; armor+offhand
        // need 5 more slots, so storage is capped 5 short here rather than
        // overwritten by the armor/offhand placement below.
        PlayerInventory inventory = target.getInventory();
        ItemStack[] storage = inventory.getStorageContents();
        int storageDisplayLimit = GuiLayout.contentSize() - 5;
        for (int i = 0; i < storage.length && i < storageDisplayLimit; i++) {
            if (storage[i] != null) {
                view.place(GuiLayout.contentSlot(i), new GuiItem(storage[i]));
            }
        }
        placeIfPresent(view, GuiLayout.CONTENT_END_SLOT - 4, inventory.getHelmet());
        placeIfPresent(view, GuiLayout.CONTENT_END_SLOT - 3, inventory.getChestplate());
        placeIfPresent(view, GuiLayout.CONTENT_END_SLOT - 2, inventory.getLeggings());
        placeIfPresent(view, GuiLayout.CONTENT_END_SLOT - 1, inventory.getBoots());
        placeIfPresent(view, GuiLayout.CONTENT_END_SLOT, inventory.getItemInOffHand());
    }

    private void placeIfPresent(GuiView view, int slot, ItemStack item) {
        if (item != null) {
            view.place(slot, new GuiItem(item));
        }
    }
}

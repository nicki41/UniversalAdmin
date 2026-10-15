package dev.universaladmin.gui;

import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * A visual-only stand-in for an empty slot in an editable inventory mirror
 * (main storage/hotbar, armor, offhand) - so a helmet slot still looks like
 * a helmet slot, and the hotbar row is visibly distinct from the rest of
 * main storage, even while empty. {@link dev.universaladmin.modules.players.gui.PlayerInventoryPage}
 * and {@link dev.universaladmin.modules.moderation.gui.InventoryInspectorPage}
 * use these.
 *
 * <p>Tagged via {@link org.bukkit.persistence.PersistentDataContainer} (the
 * {@link NamespacedKey} is built with {@link NamespacedKey#fromString(String)},
 * not a {@code Plugin} reference, since this is a shared {@code gui}-package
 * class with no plugin instance of its own to construct one from) so a
 * page's persist step can tell "this is just the placeholder" apart from a
 * real item and write {@code null} instead - see {@link #stripPlaceholder}.
 * Without that translation, closing the GUI would write a fake "Empty"
 * item straight into the target's real inventory.
 *
 * <p>Deliberately still a plain, draggable {@link GuiItem} (not a locked
 * {@link GuiButton}): a button slot is unconditionally click-cancelled by
 * {@link GuiListener} even in an editable view, which would make an empty
 * slot permanently un-fillable. The trade-off is that a placeholder can be
 * dragged around like any other item; {@link #stripPlaceholder} makes sure
 * that never writes a placeholder into a real inventory no matter which
 * slot it ends up in.
 */
public final class EmptySlotPlaceholder {

    private static final NamespacedKey KEY = NamespacedKey.fromString("universaladmin:empty-slot-placeholder");

    private EmptySlotPlaceholder() {
    }

    /** A plain gray placeholder - main storage, ender chest, or anywhere else "just empty" is all that needs saying. */
    public static GuiItem generic(Component label) {
        return of(new ItemStack(Material.GRAY_STAINED_GLASS_PANE), label);
    }

    /** Slightly lighter than {@link #generic} - main storage's hotbar row (indices 0-8), so it reads as visibly distinct from the rest. */
    public static GuiItem hotbar(Component label) {
        return of(new ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE), label);
    }

    /** A dyed-gray leather armor piece - keeps the real slot's silhouette (helmet/chestplate/leggings/boots) recognizable while empty. */
    public static GuiItem armor(Material leatherArmorPiece, Component label) {
        ItemStack stack = new ItemStack(leatherArmorPiece);
        stack.editMeta(LeatherArmorMeta.class, meta -> meta.setColor(Color.GRAY));
        return of(stack, label);
    }

    /** A grayed-out shield - the offhand slot's usual contents, empty. */
    public static GuiItem offhand(Component label) {
        return of(new ItemStack(Material.SHIELD), label);
    }

    private static GuiItem of(ItemStack stack, Component label) {
        stack.editMeta(meta -> {
            meta.displayName(label.decoration(TextDecoration.ITALIC, false));
            meta.getPersistentDataContainer().set(KEY, PersistentDataType.BYTE, (byte) 1);
        });
        return new GuiItem(stack);
    }

    /** Whether {@code item} is one of these placeholders, wherever it currently sits. */
    public static boolean isPlaceholder(ItemStack item) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(KEY, PersistentDataType.BYTE);
    }

    /**
     * The read-back translation every persist step over an editable view
     * with placeholders must apply: {@code null} for a placeholder (in its
     * home slot or - if dragged there - any other), {@code item} unchanged
     * otherwise. Never skip this: writing a tagged placeholder stack into a
     * real inventory would leave a fake "Empty - Helmet" item sitting in it.
     */
    public static ItemStack stripPlaceholder(ItemStack item) {
        return isPlaceholder(item) ? null : item;
    }

    /** Convenience for a whole slot list read back from a {@link GuiView}, in one pass - see {@link #stripPlaceholder}. */
    public static List<ItemStack> stripPlaceholders(List<ItemStack> items) {
        return items.stream().map(EmptySlotPlaceholder::stripPlaceholder).toList();
    }
}

package dev.universaladmin.gui;

import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

/**
 * A purely visual slot in a {@link GuiView} - no click behavior. Use
 * {@link GuiButton} instead for anything the viewer can interact with.
 * Immutable: building one never touches an already-open inventory.
 */
public record GuiItem(ItemStack itemStack) {

    public GuiItem {
        itemStack = itemStack.clone();
    }

    public static GuiItem of(Material material, Component name) {
        return of(material, name, List.of());
    }

    public static GuiItem of(Material material, Component name, List<Component> lore) {
        ItemStack stack = new ItemStack(material);
        stack.editMeta(meta -> {
            meta.displayName(name(name));
            if (!lore.isEmpty()) {
                meta.lore(lore.stream().map(GuiItem::loreLine).toList());
            }
        });
        return new GuiItem(stack);
    }

    /** An unnamed filler item - decorative border/spacer slots. */
    public static GuiItem filler(Material material) {
        return of(material, Component.empty());
    }

    /**
     * A {@code PLAYER_HEAD} showing {@code player}'s real skin, not the
     * generic Steve texture a bare {@code GuiItem.of(Material.PLAYER_HEAD, ...)}
     * would render. Works for both online and offline players - the client
     * resolves the skin from {@code player}'s cached profile either way, the
     * same as a vanilla player-head item obtained in survival.
     */
    public static GuiItem playerHead(OfflinePlayer player, Component name, List<Component> lore) {
        ItemStack stack = new ItemStack(Material.PLAYER_HEAD);
        stack.editMeta(SkullMeta.class, meta -> {
            meta.setOwningPlayer(player);
            meta.displayName(name(name));
            if (!lore.isEmpty()) {
                meta.lore(lore.stream().map(GuiItem::loreLine).toList());
            }
        });
        return new GuiItem(stack);
    }

    // Vanilla renders custom names and lore italic, lore in dark purple and
    // some names in their item's rarity colour (yellow, aqua). Plain menu text
    // should never inherit any of that: names default to white, lore to
    // gray, neither italic - text that sets its own colour keeps it.
    private static Component name(Component component) {
        return component.decoration(TextDecoration.ITALIC, false).colorIfAbsent(NamedTextColor.WHITE);
    }

    private static Component loreLine(Component component) {
        return component.decoration(TextDecoration.ITALIC, false).colorIfAbsent(NamedTextColor.GRAY);
    }
}

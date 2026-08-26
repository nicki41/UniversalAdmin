package dev.universaladmin.modules.moderation;

import dev.universaladmin.localization.ComponentMessages;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.localization.MessageService;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * Builds and identifies the Staff Mode tool items. Each is tagged via
 * {@link org.bukkit.persistence.PersistentDataContainer} (the first use of
 * PDC tagging in this codebase) rather than matched by display name/lore,
 * so a renamed/relocalized item is still recognized and a player crafting
 * an item with the same name never is.
 */
public final class StaffToolItems {

    public enum Tool {
        PLAYER_INSPECTOR,
        FREEZE_TOOL,
        INVENTORY_INSPECTOR,
        TELEPORT_PICKER,
        VANISH_TOGGLE,
        MODERATE_TOOL,
        TELEPORT_TOOL,
        ENDERCHEST_INSPECTOR,
        EXIT_STAFF_MODE
    }

    private static final int PLAYER_INSPECTOR_SLOT = 0;
    private static final int FREEZE_TOOL_SLOT = 1;
    private static final int INVENTORY_INSPECTOR_SLOT = 2;
    private static final int TELEPORT_PICKER_SLOT = 3;
    private static final int VANISH_TOGGLE_SLOT = 4;
    private static final int MODERATE_TOOL_SLOT = 5;
    private static final int TELEPORT_TOOL_SLOT = 6;
    private static final int ENDERCHEST_INSPECTOR_SLOT = 7;
    private static final int EXIT_STAFF_MODE_SLOT = 8;

    /** Every hotbar slot a tool kit occupies - {@link #restoreIfTampered} checks exactly these. */
    private static final int[] TOOL_SLOTS = {
        PLAYER_INSPECTOR_SLOT, FREEZE_TOOL_SLOT, INVENTORY_INSPECTOR_SLOT, TELEPORT_PICKER_SLOT,
        VANISH_TOGGLE_SLOT, MODERATE_TOOL_SLOT, TELEPORT_TOOL_SLOT, ENDERCHEST_INSPECTOR_SLOT, EXIT_STAFF_MODE_SLOT
    };

    private record ToolSpec(int slot, Material material, String labelKey) {}

    private static final Map<Tool, ToolSpec> SPECS = Map.ofEntries(
            Map.entry(Tool.PLAYER_INSPECTOR, new ToolSpec(PLAYER_INSPECTOR_SLOT, Material.PLAYER_HEAD, "moderation.gui.staffmode.tool.player-inspector")),
            Map.entry(Tool.FREEZE_TOOL, new ToolSpec(FREEZE_TOOL_SLOT, Material.PACKED_ICE, "moderation.gui.staffmode.tool.freeze")),
            Map.entry(Tool.INVENTORY_INSPECTOR, new ToolSpec(INVENTORY_INSPECTOR_SLOT, Material.CHEST, "moderation.gui.staffmode.tool.inventory-inspector")),
            Map.entry(Tool.TELEPORT_PICKER, new ToolSpec(TELEPORT_PICKER_SLOT, Material.NETHER_STAR, "moderation.gui.staffmode.tool.teleport-picker")),
            Map.entry(Tool.VANISH_TOGGLE, new ToolSpec(VANISH_TOGGLE_SLOT, Material.GLASS, "moderation.gui.staffmode.tool.vanish")),
            Map.entry(Tool.MODERATE_TOOL, new ToolSpec(MODERATE_TOOL_SLOT, Material.ANVIL, "moderation.gui.staffmode.tool.moderate")),
            Map.entry(Tool.TELEPORT_TOOL, new ToolSpec(TELEPORT_TOOL_SLOT, Material.COMPASS, "moderation.gui.staffmode.tool.teleport")),
            Map.entry(Tool.ENDERCHEST_INSPECTOR, new ToolSpec(ENDERCHEST_INSPECTOR_SLOT, Material.ENDER_CHEST, "moderation.gui.staffmode.tool.enderchest-inspector")),
            Map.entry(Tool.EXIT_STAFF_MODE, new ToolSpec(EXIT_STAFF_MODE_SLOT, Material.BARRIER, "moderation.gui.staffmode.tool.exit")));

    /**
     * Tools that target another player and therefore render that player's
     * live status (Frozen/Vanished) in their lore while held - see
     * {@link #updateHeldTool}. {@code TELEPORT_PICKER}/{@code VANISH_TOGGLE}/
     * {@code EXIT_STAFF_MODE} act on the staff member themself (or on
     * whoever they pick from a list, not whoever they're looking at), not a
     * crosshair target, so they're excluded.
     */
    private static final Set<Tool> STATUS_AWARE_TOOLS = EnumSet.of(
            Tool.PLAYER_INSPECTOR, Tool.FREEZE_TOOL, Tool.INVENTORY_INSPECTOR, Tool.MODERATE_TOOL,
            Tool.TELEPORT_TOOL, Tool.ENDERCHEST_INSPECTOR);

    private final NamespacedKey key;
    private final MessageService messages;

    public StaffToolItems(Plugin plugin, MessageService messages) {
        this.key = new NamespacedKey(plugin, "staff-tool");
        this.messages = messages;
    }

    /** Clears {@code player}'s hotbar and places every tool. */
    public void giveKit(Player player) {
        for (Map.Entry<Tool, ToolSpec> entry : SPECS.entrySet()) {
            ToolSpec spec = entry.getValue();
            player.getInventory().setItem(spec.slot(), build(entry.getKey(), spec.material(), spec.labelKey()));
        }
    }

    /**
     * Re-places any tool slot that no longer holds its expected tagged tool -
     * the backstop for {@link StaffModeGuardListener}'s click/drag/drop
     * cancellation, in case some other plugin or an untested vanilla edge
     * case still manages to move/remove one. Called periodically, not on
     * every inventory change, so this is a cheap "did anything slip through"
     * check, not the primary defense.
     */
    public void restoreIfTampered(Player player) {
        for (int slot : TOOL_SLOTS) {
            if (toolOf(player.getInventory().getItem(slot)).isEmpty()) {
                giveKit(player);
                return;
            }
        }
    }

    /**
     * Refreshes every {@link #STATUS_AWARE_TOOLS status-aware} tool slot:
     * whichever one is currently {@code held} shows the {@code target}'s live
     * Frozen/Vanished status in its lore (real skin/name for the Player
     * Inspector, target name for the rest); every other status-aware slot is
     * reset to its plain default look - except {@link Tool#PLAYER_INSPECTOR},
     * which always shows the target's real head whenever one exists,
     * regardless of which tool is actually held (staff want to see who
     * they're looking at without needing to switch to that specific tool
     * first). Re-rendered every tick a target exists rather than only on
     * change, so e.g. freezing the very player being looked at shows up
     * within one tick without the staff member needing to look away and back.
     */
    public void updateHeldTool(Player staff, Tool held, Player target, boolean frozen, boolean vanished) {
        for (Map.Entry<Tool, ToolSpec> entry : SPECS.entrySet()) {
            Tool tool = entry.getKey();
            if (!STATUS_AWARE_TOOLS.contains(tool)) {
                continue;
            }
            ToolSpec spec = entry.getValue();
            boolean showTargeted = target != null && (tool == held || tool == Tool.PLAYER_INSPECTOR);
            ItemStack item = showTargeted
                    ? targetedItem(tool, spec, target, frozen, vanished)
                    : build(tool, spec.material(), spec.labelKey());
            staff.getInventory().setItem(spec.slot(), item);
        }
    }

    private ItemStack targetedItem(Tool tool, ToolSpec spec, OfflinePlayer target, boolean frozen, boolean vanished) {
        ItemStack item = new ItemStack(spec.material());
        item.editMeta(meta -> {
            meta.displayName(Component.text(target.getName() == null ? "?" : target.getName()).decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(
                    statusLine("moderation.gui.staffmode.tool.status-frozen", frozen),
                    statusLine("moderation.gui.staffmode.tool.status-vanished", vanished)));
            meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, tool.name());
            if (meta instanceof SkullMeta skullMeta) {
                skullMeta.setOwningPlayer(target);
            }
        });
        return item;
    }

    private Component statusLine(String labelKey, boolean value) {
        String status = messages.get(MessageKey.of(value ? "moderation.gui.status.on" : "moderation.gui.status.off"));
        return ComponentMessages.render(messages.get(MessageKey.of(labelKey), status)).decoration(TextDecoration.ITALIC, false);
    }

    /** Which tool {@code item} is, if it's tagged as one at all. */
    public Optional<Tool> toolOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return Optional.empty();
        }
        String value = item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(Tool.valueOf(value));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private ItemStack build(Tool tool, Material material, String labelKey) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> {
            meta.displayName(ComponentMessages.render(messages.get(MessageKey.of(labelKey))).decoration(TextDecoration.ITALIC, false));
            meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, tool.name());
        });
        return item;
    }
}

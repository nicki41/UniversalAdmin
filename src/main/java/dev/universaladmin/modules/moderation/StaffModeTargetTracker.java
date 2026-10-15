package dev.universaladmin.modules.moderation;

import dev.universaladmin.localization.ComponentMessages;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.localization.MessageService;
import dev.universaladmin.settings.SettingsService;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Runs periodically (see {@code ModerationModule#onEnable}'s repeating
 * task) for every online, Staff-Mode-active player: refreshes whichever
 * status-aware tool is currently held to show whoever is in the staff
 * member's crosshair (see {@link StaffToolItems#updateHeldTool}), sends a
 * persistent actionbar naming the same target, and calls
 * {@link StaffToolItems#restoreIfTampered} as the tool-kit tampering
 * backstop - see that method's javadoc for why this isn't the primary
 * defense (that's {@link StaffModeGuardListener}'s click/drag/drop
 * cancellation).
 *
 * <p>Re-renders the held tool/actionbar every tick a target exists rather
 * than only on target-change, so a status flip (e.g. freezing the very
 * player being looked at while holding the Freeze Tool) shows up within one
 * tick instead of requiring the staff member to look away and back - cheap
 * enough given how few players are ever in Staff Mode at once. Resending the
 * actionbar this often also keeps Minecraft's own fade-out timer from ever
 * expiring, which is what makes it read as "persistent" to the player.
 */
final class StaffModeTargetTracker {

    private final StaffModeState staffModeState;
    private final StaffToolItems toolItems;
    private final FreezeRuntimeState freezeState;
    private final VanishService vanishService;
    private final SettingsService settings;
    private final MessageService messages;

    StaffModeTargetTracker(
            StaffModeState staffModeState, StaffToolItems toolItems, FreezeRuntimeState freezeState,
            VanishService vanishService, SettingsService settings, MessageService messages) {
        this.staffModeState = staffModeState;
        this.toolItems = toolItems;
        this.freezeState = freezeState;
        this.vanishService = vanishService;
        this.settings = settings;
        this.messages = messages;
    }

    void tick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!staffModeState.isActive(player.getUniqueId())) {
                continue;
            }
            toolItems.restoreIfTampered(player);

            Player target = targetedPlayer(player);
            boolean frozen = target != null && freezeState.isFrozen(target.getUniqueId());
            boolean vanished = target != null && vanishService.isVanished(target.getUniqueId());
            StaffToolItems.Tool held = toolItems.toolOf(player.getInventory().getItemInMainHand()).orElse(null);
            toolItems.updateHeldTool(player, held, target, frozen, vanished);
            player.sendActionBar(actionbarFor(target));
        }
    }

    private Component actionbarFor(Player target) {
        return target != null
                ? ComponentMessages.render(messages.get(MessageKey.of("moderation.gui.staffmode.actionbar.target"), target.getName()))
                : ComponentMessages.render(messages.get(MessageKey.of("moderation.gui.staffmode.actionbar.no-target")));
    }

    private Player targetedPlayer(Player staff) {
        return StaffModeTargeting.targetedPlayer(staff, settings.get(ModerationSettings.STAFFMODE_TARGET_RANGE_BLOCKS));
    }
}

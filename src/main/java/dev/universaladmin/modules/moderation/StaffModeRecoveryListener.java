package dev.universaladmin.modules.moderation;

import dev.universaladmin.localization.ComponentMessages;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.localization.MessageService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * "Beim nächsten Login Recovery durchführen" - if the joining player has a
 * pending staff-mode snapshot (server crashed mid-session, see {@code
 * StaffModeService} class javadoc for why that's the only way one can
 * exist), restore it immediately and tell them. Pure event-to-service-call
 * translation.
 */
public final class StaffModeRecoveryListener implements Listener {

    private final StaffModeService staffModeService;
    private final MessageService messages;

    public StaffModeRecoveryListener(StaffModeService staffModeService, MessageService messages) {
        this.staffModeService = staffModeService;
        this.messages = messages;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        staffModeService.recover(player.getUniqueId()).thenAccept(outcome -> {
            if (outcome == StaffModeService.RecoveryOutcome.RECOVERED) {
                player.sendMessage(ComponentMessages.render(messages.get(MessageKey.of("moderation.enforcement.staffmode-recovered"))));
            }
        });
    }
}

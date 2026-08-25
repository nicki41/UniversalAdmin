package dev.universaladmin.modules.moderation;

import dev.universaladmin.localization.ComponentMessages;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.localization.MessageService;
import dev.universaladmin.settings.SettingsService;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Cancels chat for an actively muted player - pure event-to-service-call
 * translation (see {@link ModerationJoinListener}'s javadoc for the same
 * rule). Uses the modern Paper {@link AsyncChatEvent}, not the deprecated
 * Bukkit {@code AsyncPlayerChatEvent} - it already fires off the main
 * thread, same blocking-{@code join()} idiom as the join check.
 */
public final class ModerationChatListener implements Listener {

    private final PunishmentService punishmentService;
    private final MessageService messages;
    private final SettingsService settings;

    public ModerationChatListener(PunishmentService punishmentService, MessageService messages, SettingsService settings) {
        this.punishmentService = punishmentService;
        this.messages = messages;
        this.settings = settings;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        Optional<Punishment> mute = punishmentService.activeMute(player.getUniqueId()).join();
        mute.ifPresent(punishment -> {
            event.setCancelled(true);
            player.sendMessage(muteMessage(punishment));
        });
    }

    private Component muteMessage(Punishment punishment) {
        String key = punishment.permanent() ? "moderation.enforcement.muted-permanent" : "moderation.enforcement.muted-temp";
        String reason = reasonOrDefault(punishment);
        return punishment.permanent()
                ? ComponentMessages.render(messages.get(MessageKey.of(key), reason))
                : ComponentMessages.render(messages.get(MessageKey.of(key), reason, ModerationFormat.expiry(punishment.expiresAt(), settings, messages)));
    }

    private String reasonOrDefault(Punishment punishment) {
        return punishment.reason() == null || punishment.reason().isBlank()
                ? messages.get(MessageKey.of("moderation.enforcement.no-reason"))
                : punishment.reason();
    }
}

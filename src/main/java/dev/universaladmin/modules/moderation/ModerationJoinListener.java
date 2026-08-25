package dev.universaladmin.modules.moderation;

import dev.universaladmin.localization.ComponentMessages;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.localization.MessageService;
import dev.universaladmin.settings.SettingsService;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

/**
 * Denies login for an actively banned target - pure event-to-service-call
 * translation, no business logic here (see docs/development/architecture-rules.md's "keine
 * Bukkit-Event-Listener mit Logik" rule; the actual "is this player banned"
 * decision lives in {@link PunishmentService}). {@link AsyncPlayerPreLoginEvent}
 * fires off the main thread (before a {@code Player} object even exists),
 * which is exactly what a DB-backed ban lookup needs - blocking on {@link
 * java.util.concurrent.CompletableFuture#join()} here is the standard idiom
 * for this event, not a main-thread stall.
 */
public final class ModerationJoinListener implements Listener {

    private final PunishmentService punishmentService;
    private final FreezeRuntimeState freezeRuntimeState;
    private final MessageService messages;
    private final SettingsService settings;

    public ModerationJoinListener(
            PunishmentService punishmentService, FreezeRuntimeState freezeRuntimeState, MessageService messages, SettingsService settings) {
        this.punishmentService = punishmentService;
        this.freezeRuntimeState = freezeRuntimeState;
        this.messages = messages;
        this.settings = settings;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }
        Optional<Punishment> ban = punishmentService.activeBan(event.getUniqueId()).join();
        if (ban.isEmpty() && event.getAddress() != null) {
            ban = punishmentService.activeIpBan(event.getAddress().getHostAddress()).join();
        }
        if (ban.isPresent()) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, banMessage(ban.get()));
            return;
        }
        // Populates FreezeRuntimeState here (async-safe context) so
        // FreezeGuardListener's hot-path checks never need to query the
        // database themselves - see that class's javadoc.
        if (punishmentService.activeFreeze(event.getUniqueId()).join().isPresent()) {
            freezeRuntimeState.setFrozen(event.getUniqueId(), true);
        }
    }

    private Component banMessage(Punishment punishment) {
        String key = punishment.permanent() ? "moderation.enforcement.banned-permanent" : "moderation.enforcement.banned-temp";
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

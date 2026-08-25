package dev.universaladmin.modules.whitelist;

import dev.universaladmin.action.ActionContext;
import dev.universaladmin.action.ActionExecutor;
import dev.universaladmin.action.Actor;
import dev.universaladmin.action.Source;
import dev.universaladmin.localization.ComponentMessages;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.localization.MessageService;
import dev.universaladmin.modules.whitelist.action.WhitelistActionIds;
import java.time.Instant;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

/**
 * Catches the one case vanilla's own whitelist check can't: a player who is
 * still natively whitelisted but whose UniversalAdmin-tracked entry has
 * already expired (the periodic sweep just hasn't reached them yet). Pure
 * event-to-service-call translation, no business logic here (see docs/development/architecture-rules.md's
 * "no Bukkit event listeners with logic" rule) - the expiry decision is
 * {@link WhitelistEntry#isExpired}, and the actual cleanup runs through
 * {@link ActionExecutor} exactly like the periodic sweep (see {@link
 * WhitelistExpirySweeper}), so both paths produce the same audit entry
 * shape.
 *
 * <p>Uses {@link AsyncPlayerPreLoginEvent} (see {@code ModerationJoinListener}'s
 * ban check) - fires off the main thread, before a {@code Player} object
 * exists, exactly what a DB-backed lookup needs; blocking on {@link
 * java.util.concurrent.CompletableFuture#join()} here is the standard idiom
 * for this event, not a main-thread stall.
 */
public final class WhitelistJoinListener implements Listener {

    private final WhitelistEntryRepository repository;
    private final ActionExecutor actionExecutor;
    private final MessageService messages;

    public WhitelistJoinListener(WhitelistEntryRepository repository, ActionExecutor actionExecutor, MessageService messages) {
        this.repository = repository;
        this.actionExecutor = actionExecutor;
        this.messages = messages;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (!Bukkit.hasWhitelist() || event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }
        Optional<WhitelistEntry> managed = repository.findById(event.getUniqueId()).join();
        if (managed.isEmpty()) {
            return;
        }
        WhitelistEntry entry = managed.get();
        if (entry.source() != WhitelistSource.UNIVERSAL_ADMIN || !entry.isExpired(Instant.now())) {
            return;
        }
        event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_WHITELIST, kickMessage());
        // Fire-and-forget: the event is already denied above; this just
        // brings the native whitelist and our own row back in sync, through
        // the same audited path the periodic sweep uses.
        actionExecutor.execute(WhitelistActionIds.REMOVE,
                new ActionContext(Actor.system("whitelist-expiry"), Source.SYSTEM), event.getUniqueId());
    }

    private Component kickMessage() {
        return ComponentMessages.render(messages.get(MessageKey.of("whitelist.enforcement.expired")));
    }
}

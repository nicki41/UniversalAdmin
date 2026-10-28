package dev.universaladmin.modules.moderation;

import java.util.Optional;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

/**
 * Denies login for an actively banned target - pure event-to-service-call
 * translation, no business logic here (see docs/development/architecture-rules.md's "no
 * Bukkit event listeners with logic" rule; the actual "is this player banned"
 * decision lives in {@link PunishmentService}). {@link AsyncPlayerPreLoginEvent}
 * fires off the main thread (before a {@code Player} object even exists),
 * which is exactly what a DB-backed ban lookup needs - blocking on {@link
 * java.util.concurrent.CompletableFuture#join()} here is the standard idiom
 * for this event, not a main-thread stall. In network mode the database is
 * shared, so a ban from any server applies here as well.
 */
public final class ModerationJoinListener implements Listener {

    private final PunishmentService punishmentService;
    private final FreezeRuntimeState freezeRuntimeState;
    private final PunishmentEnforcer enforcer;

    public ModerationJoinListener(PunishmentService punishmentService, FreezeRuntimeState freezeRuntimeState, PunishmentEnforcer enforcer) {
        this.punishmentService = punishmentService;
        this.freezeRuntimeState = freezeRuntimeState;
        this.enforcer = enforcer;
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
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, enforcer.banScreen(ban.get()));
            return;
        }
        // Populates FreezeRuntimeState here (async-safe context) so
        // FreezeGuardListener's hot-path checks never need to query the
        // database themselves - see that class's javadoc. Also how a freeze
        // follows a player who switches servers in network mode.
        punishmentService.activeFreeze(event.getUniqueId()).join().ifPresent(freeze ->
                freezeRuntimeState.freeze(event.getUniqueId(), freeze.actorName(), freeze.createdAt()));
    }
}

package dev.universaladmin.modules.moderation;

import dev.universaladmin.action.ActionContext;
import dev.universaladmin.action.ActionExecutor;
import dev.universaladmin.action.Actor;
import dev.universaladmin.action.Source;
import dev.universaladmin.modules.moderation.action.ModerationActionIds;
import java.util.UUID;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * "Disconnect while frozen" audit + staff notification - routed through
 * {@link ActionExecutor} with a system {@link Actor} rather than calling
 * {@code AuditService}/{@code NotificationService} directly, since a module
 * is never allowed to write audit entries outside that one hook (see
 * {@code ModerationActionIds#FREEZE_DISCONNECT_NOTICE}'s javadoc).
 */
public final class FreezeDisconnectListener implements Listener {

    private final FreezeRuntimeState freezeState;
    private final ActionExecutor actionExecutor;

    public FreezeDisconnectListener(FreezeRuntimeState freezeState, ActionExecutor actionExecutor) {
        this.freezeState = freezeState;
        this.actionExecutor = actionExecutor;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        if (freezeState.isFrozen(id)) {
            ActionContext context = new ActionContext(Actor.system("freeze-monitor"), Source.SYSTEM);
            actionExecutor.execute(ModerationActionIds.FREEZE_DISCONNECT_NOTICE, context, id);
        }
        // Harmless either way (freeze is re-derived from the DB on next
        // login, see ModerationJoinListener) - just avoids an unbounded
        // in-memory set across many distinct freeze/unfreeze/reconnect cycles.
        freezeState.setFrozen(id, false);
    }
}

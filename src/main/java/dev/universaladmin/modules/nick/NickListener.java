package dev.universaladmin.modules.nick;

import com.destroystokyo.paper.event.player.PlayerPostRespawnEvent;
import dev.universaladmin.action.ActionContext;
import dev.universaladmin.action.ActionExecutor;
import dev.universaladmin.action.Actor;
import dev.universaladmin.action.Source;
import dev.universaladmin.core.PlayerRef;
import dev.universaladmin.localization.ComponentMessages;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.localization.MessageService;
import dev.universaladmin.modules.nick.action.NickActions;
import dev.universaladmin.network.NetworkService;
import dev.universaladmin.status.Nicks;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Nicks across logins, translated into {@link NickService} calls:
 * <ul>
 *   <li>The stored nick is loaded while the player logs in (async, like the
 *       ban check) and put on first thing when they join - before Paper
 *       announces them, so nobody ever sees the real name, and before the
 *       other modules record them, which then ask
 *       {@link dev.universaladmin.core.PlayerNames#real} for the real one.
 *       With {@code nick.restore-on-rejoin} off only a server switch keeps
 *       it (same rule as vanish: still listed on another server = switching).</li>
 *   <li>A player whose real name is somebody's nick takes their name back:
 *       that nick ends.</li>
 *   <li>Death messages lose the UUID hover of nicked players.</li>
 * </ul>
 */
final class NickListener implements Listener {

    private final NickService nicks;
    private final NickRuntimeState state;
    private final Nicks view;
    private final NetworkService network;
    private final ActionExecutor actions;
    private final MessageService messages;
    private final Logger logger;

    NickListener(NickService nicks, NickRuntimeState state, Nicks view, NetworkService network, ActionExecutor actions,
            MessageService messages, Logger logger) {
        this.nicks = nicks;
        this.state = state;
        this.view = view;
        this.network = network;
        this.actions = actions;
        this.messages = messages;
        this.logger = logger;
    }

    @EventHandler
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }
        UUID playerId = event.getUniqueId();
        try {
            Optional<NickRecord> stored = nicks.stored(playerId).join();
            if (stored.isEmpty()) {
                return;
            }
            if (nicks.restoreOnRejoin() || switchingServers(playerId)) {
                state.setPending(playerId, stored.get());
            } else {
                nicks.delete(playerId);
            }
        } catch (RuntimeException e) {
            logger.log(Level.WARNING, "Could not load the nick of " + event.getName() + " - they join without it", e);
        }
    }

    private boolean switchingServers(UUID playerId) {
        return network.enabled() && network.player(playerId)
                .filter(elsewhere -> !elsewhere.server().equalsIgnoreCase(network.serverName()))
                .isPresent();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        releaseNameOf(player);
        Optional<NickRecord> pending = state.takePending(player.getUniqueId());
        if (pending.isPresent() && inUse(pending.get().name(), player.getUniqueId())) {
            // Someone really called that (or nicked so) arrived while they were away.
            nicks.delete(player.getUniqueId());
            player.sendMessage(ComponentMessages.render(messages.get(MessageKey.of("nick.lost-name"), pending.get().name())));
            pending = Optional.empty();
        }
        pending.ifPresent(nick -> {
            nicks.wear(player, nick);
            event.joinMessage(NickProfiles.joinMessage(event.joinMessage(), nick.name()));
        });
        nicks.arrive(player);
    }

    /** The joining player's own name is someone's nick - here or on another server: that nick ends. */
    private void releaseNameOf(Player player) {
        view.byName(player.getName())
                .filter(nick -> !nick.playerId().equals(player.getUniqueId()))
                .ifPresent(nick -> {
                    ActionContext system = new ActionContext(Actor.system("Nick"), Source.SYSTEM);
                    actions.execute(NickActions.CLEAR, system, new PlayerRef(nick.playerId(), nick.realName()));
                });
    }

    private boolean inUse(String name, UUID self) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!online.getUniqueId().equals(self) && online.getName().equalsIgnoreCase(name)) {
                return true;
            }
        }
        return view.byName(name).filter(nick -> !nick.playerId().equals(self)).isPresent()
                || network.player(name).filter(elsewhere -> !self.equals(elsewhere.id())).isPresent();
    }

    @EventHandler
    public void onRespawn(PlayerPostRespawnEvent event) {
        nicks.respawned(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        Set<UUID> nicked = new HashSet<>();
        state.allActive().forEach(active -> nicked.add(active.nick().playerId()));
        if (!nicked.isEmpty()) {
            event.deathMessage(NickProfiles.withoutEntityHovers(event.deathMessage(), nicked));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) {
        state.takePending(event.getPlayer().getUniqueId());
        nicks.leave(event.getPlayer());
    }
}

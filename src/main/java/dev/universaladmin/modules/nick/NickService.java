package dev.universaladmin.modules.nick;

import com.destroystokyo.paper.profile.PlayerProfile;
import dev.universaladmin.localization.ComponentMessages;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.localization.MessageService;
import dev.universaladmin.network.NetworkMessage;
import dev.universaladmin.network.NetworkService;
import dev.universaladmin.scheduler.TaskScheduler;
import dev.universaladmin.settings.SettingsService;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Puts nicks on and takes them off ({@link NickProfiles}), keeps
 * {@code ua_nicks} and the {@link NickRuntimeState} in step, and tells the
 * other servers of a network. Methods taking a {@link Player} run on the
 * main thread; storage runs async.
 */
public final class NickService {

    /** A nick was set or removed on one server - the others reload that player's row. */
    static final String NETWORK_TOPIC = "core:nick";

    private final NickRepository repository;
    private final NickRuntimeState state;
    private final NetworkService network;
    private final SettingsService settings;
    private final MessageService messages;
    private final TaskScheduler scheduler;
    private final Plugin plugin;
    private final Logger logger;
    /** Who a nicked player was hidden from when they left - shown to them again when the player comes back. */
    private final Map<UUID, Set<UUID>> hiddenAtQuit = new ConcurrentHashMap<>();

    NickService(NickRepository repository, NickRuntimeState state, NetworkService network, SettingsService settings,
            MessageService messages, TaskScheduler scheduler, Plugin plugin, Logger logger) {
        this.repository = repository;
        this.state = state;
        this.network = network;
        this.settings = settings;
        this.messages = messages;
        this.scheduler = scheduler;
        this.plugin = plugin;
        this.logger = logger;
    }

    public Optional<NickRecord> active(UUID playerId) {
        return state.active(playerId).map(NickRuntimeState.Active::nick);
    }

    public boolean restoreOnRejoin() {
        return settings.get(NickSettings.RESTORE_ON_REJOIN);
    }

    /** Shows {@code player} under {@code nick} from now on - a second nick replaces the first. Main thread only. */
    public void wear(Player player, NickRecord nick) {
        Optional<NickRuntimeState.Active> current = state.active(player.getUniqueId());
        PlayerProfile real = current.map(NickRuntimeState.Active::realProfile).orElseGet(player::getPlayerProfile);
        Component realDisplayName = current.map(NickRuntimeState.Active::realDisplayName).orElseGet(player::displayName);
        state.activate(player.getUniqueId(), current.map(active -> active.with(nick))
                .orElseGet(() -> new NickRuntimeState.Active(nick, real, realDisplayName, false)));
        NickProfiles.wear(player, NickProfiles.disguise(real, nick));
    }

    /** Shows {@code player} as themselves again; the stored nick is left alone. Main thread only. */
    public Optional<NickRecord> takeOff(Player player) {
        Optional<NickRuntimeState.Active> active = state.deactivate(player.getUniqueId());
        active.ifPresent(nick -> NickProfiles.giveBack(player, nick));
        return active.map(NickRuntimeState.Active::nick);
    }

    /**
     * Called while {@code player} quits. Paper finds online players by the
     * name they joined with; giving their own profile back before they are
     * gone keeps that right. Hidden from everyone first, so nobody sees the
     * real name flash up - per viewer, as Paper would save a "hidden by
     * default" in the player's data. After a respawn Paper knows them by the
     * nick as well - then the nick stays on, as only one of the two names
     * can go.
     */
    public void leave(Player player) {
        Optional<NickRuntimeState.Active> active = state.deactivate(player.getUniqueId());
        if (active.isEmpty() || active.get().respawned()) {
            return;
        }
        Set<UUID> viewers = new HashSet<>();
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (!viewer.equals(player) && viewer.canSee(player)) {
                viewer.hidePlayer(plugin, player);
                viewers.add(viewer.getUniqueId());
            }
        }
        hiddenAtQuit.put(player.getUniqueId(), viewers);
        NickProfiles.giveBack(player, active.get());
    }

    /** Shows {@code player} again to whoever they were hidden from when they last left nicked. Main thread only. */
    public void arrive(Player player) {
        Set<UUID> viewers = hiddenAtQuit.remove(player.getUniqueId());
        if (viewers == null) {
            return;
        }
        for (UUID viewerId : viewers) {
            Player viewer = Bukkit.getPlayer(viewerId);
            if (viewer != null) {
                viewer.showPlayer(plugin, player);
            }
        }
    }

    public void respawned(UUID playerId) {
        state.markRespawned(playerId);
    }

    /** Stores {@code nick} (replacing the player's previous one) and tells the network. */
    public CompletableFuture<NickRecord> save(NickRecord nick) {
        return repository.save(nick).thenApply(saved -> {
            state.store(saved);
            announce(saved.playerId(), "set");
            return saved;
        });
    }

    /** Deletes the player's stored nick and tells the network. */
    public CompletableFuture<Void> delete(UUID playerId) {
        return repository.deleteById(playerId).thenRun(() -> {
            state.forget(playerId);
            announce(playerId, "clear");
        });
    }

    public CompletableFuture<Optional<NickRecord>> stored(UUID playerId) {
        return repository.findById(playerId);
    }

    public CompletableFuture<Optional<NickRecord>> storedByName(String name) {
        return repository.findByName(name);
    }

    /** Reloads every stored nick - the other servers' nicks, and a safety net for missed network messages. */
    public void reload() {
        repository.findAll().whenComplete((nicks, error) -> {
            if (error != null) {
                logger.log(Level.WARNING, "Could not reload the nicks", error);
            } else {
                state.replaceStored(nicks);
            }
        });
    }

    /** Puts every stored nick back on its player - for players who are online when the module starts. */
    public void wearStored() {
        repository.findAll().whenComplete((nicks, error) -> {
            if (error != null) {
                logger.log(Level.WARNING, "Could not load the nicks", error);
                return;
            }
            state.replaceStored(nicks);
            scheduler.runOnMainThread(() -> nicks.forEach(nick -> {
                Player player = Bukkit.getPlayer(nick.playerId());
                if (player != null && state.active(nick.playerId()).isEmpty()) {
                    wear(player, nick);
                }
            }));
        });
    }

    /** Takes every nick off before the module stops - they come back when it starts again. Main thread only. */
    public void takeAllOff() {
        for (NickRuntimeState.Active active : state.allActive()) {
            Player player = Bukkit.getPlayer(active.nick().playerId());
            if (player != null) {
                takeOff(player);
            }
        }
    }

    /** Another server changed a nick: reload the row, and take the nick off here if it was removed there. */
    void onNetworkChange(NetworkMessage message) {
        UUID playerId;
        try {
            playerId = UUID.fromString(message.get("player", ""));
        } catch (IllegalArgumentException e) {
            return;
        }
        repository.findById(playerId).whenComplete((stored, error) -> {
            if (error != null) {
                logger.log(Level.WARNING, "Could not reload the nick of " + playerId, error);
                return;
            }
            stored.ifPresentOrElse(state::store, () -> state.forget(playerId));
            if (stored.isEmpty()) {
                scheduler.runOnMainThread(() -> {
                    Player player = Bukkit.getPlayer(playerId);
                    if (player != null && takeOff(player).isPresent()) {
                        player.sendMessage(ComponentMessages.render(messages.get(MessageKey.of("nick.removed-by-staff"))));
                    }
                });
            }
        });
    }

    private void announce(UUID playerId, String change) {
        network.publish(NETWORK_TOPIC, Map.of("player", playerId.toString(), "change", change));
    }
}

package dev.universaladmin.modules.nick;

import com.destroystokyo.paper.profile.PlayerProfile;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;

/**
 * What the Nick module knows in memory - read by everything that runs often
 * ({@link NickView}, name lookups, displays), never the database:
 * <ul>
 *   <li>{@code active}: players nicked on this server right now, with the
 *       profile and display name to give back when the nick ends;</li>
 *   <li>{@code pending}: nicks loaded while a player logs in, put on when
 *       they join (see {@link NickListener});</li>
 *   <li>{@code stored}: every row of {@code ua_nicks}, reloaded every minute
 *       and on a change anywhere in the network - the nicks of players on
 *       other servers come from here.</li>
 * </ul>
 */
final class NickRuntimeState {

    /**
     * @param respawned the player respawned while nicked - Paper then also
     *                  knows them by the nick, see {@link NickListener#onQuit}
     */
    record Active(NickRecord nick, PlayerProfile realProfile, Component realDisplayName, boolean respawned) {

        Active respawn() {
            return new Active(nick, realProfile, realDisplayName, true);
        }

        Active with(NickRecord changed) {
            return new Active(changed, realProfile, realDisplayName, respawned);
        }
    }

    private final Map<UUID, Active> active = new ConcurrentHashMap<>();
    private final Map<UUID, NickRecord> pending = new ConcurrentHashMap<>();
    private final Map<UUID, NickRecord> stored = new ConcurrentHashMap<>();

    Optional<Active> active(UUID playerId) {
        return Optional.ofNullable(active.get(playerId));
    }

    Collection<Active> allActive() {
        return List.copyOf(active.values());
    }

    void activate(UUID playerId, Active state) {
        active.put(playerId, state);
        stored.put(playerId, state.nick());
    }

    Optional<Active> deactivate(UUID playerId) {
        return Optional.ofNullable(active.remove(playerId));
    }

    void markRespawned(UUID playerId) {
        active.computeIfPresent(playerId, (id, state) -> state.respawn());
    }

    void setPending(UUID playerId, NickRecord nick) {
        pending.put(playerId, nick);
    }

    Optional<NickRecord> takePending(UUID playerId) {
        return Optional.ofNullable(pending.remove(playerId));
    }

    Optional<NickRecord> stored(UUID playerId) {
        return Optional.ofNullable(stored.get(playerId));
    }

    Collection<NickRecord> allStored() {
        return List.copyOf(stored.values());
    }

    void store(NickRecord nick) {
        stored.put(nick.playerId(), nick);
    }

    void forget(UUID playerId) {
        stored.remove(playerId);
    }

    /** Replaces what is stored with a fresh read of the table - nicks active here win over a read that raced with a change. */
    void replaceStored(Collection<NickRecord> fresh) {
        Map<UUID, NickRecord> next = new ConcurrentHashMap<>();
        fresh.forEach(nick -> next.put(nick.playerId(), nick));
        active.forEach((id, state) -> next.put(id, state.nick()));
        stored.keySet().retainAll(next.keySet());
        stored.putAll(next);
    }
}

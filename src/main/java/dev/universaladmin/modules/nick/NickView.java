package dev.universaladmin.modules.nick;

import dev.universaladmin.network.NetworkPlayer;
import dev.universaladmin.network.NetworkService;
import dev.universaladmin.status.Nicks;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The {@link Nicks} service: the nicks active on this server, plus - in
 * network mode - the stored nicks of players the network lists on another
 * server. A stored nick of a player who is offline is not shown anywhere,
 * so it is not active.
 */
final class NickView implements Nicks {

    private final NickRuntimeState state;
    private final NetworkService network;

    NickView(NickRuntimeState state, NetworkService network) {
        this.state = state;
        this.network = network;
    }

    @Override
    public Optional<Nick> nick(UUID playerId) {
        if (playerId == null) {
            return Optional.empty();
        }
        Optional<NickRuntimeState.Active> here = state.active(playerId);
        if (here.isPresent()) {
            return Optional.of(local(here.get().nick()));
        }
        return elsewhere(playerId).flatMap(server -> state.stored(playerId).map(nick -> nick(nick, server)));
    }

    @Override
    public Optional<Nick> byName(String name) {
        if (name == null) {
            return Optional.empty();
        }
        return active().stream().filter(nick -> nick.name().equalsIgnoreCase(name)).findFirst();
    }

    @Override
    public List<Nick> active() {
        List<Nick> nicks = new ArrayList<>();
        state.allActive().forEach(active -> nicks.add(local(active.nick())));
        if (network.enabled()) {
            for (NickRecord stored : state.allStored()) {
                if (state.active(stored.playerId()).isEmpty()) {
                    elsewhere(stored.playerId()).ifPresent(server -> nicks.add(nick(stored, server)));
                }
            }
        }
        return nicks;
    }

    private Optional<String> elsewhere(UUID playerId) {
        if (!network.enabled()) {
            return Optional.empty();
        }
        return network.player(playerId).map(NetworkPlayer::server).filter(server -> !server.equalsIgnoreCase(network.serverName()));
    }

    private Nick local(NickRecord nick) {
        return nick(nick, network.enabled() ? network.serverName() : null);
    }

    private static Nick nick(NickRecord nick, String server) {
        return new Nick(nick.playerId(), nick.name(), nick.realName(), nick.skinOf(), server);
    }
}

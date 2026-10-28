package dev.universaladmin.modules.nick;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.universaladmin.network.NetworkPlayer;
import dev.universaladmin.network.NetworkService;
import dev.universaladmin.status.Nicks;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** What {@link NickView} counts as shown right now - here, elsewhere in the network, or not at all. */
class NickViewTest {

    private final UUID here = UUID.randomUUID();
    private final UUID elsewhere = UUID.randomUUID();
    private final UUID offline = UUID.randomUUID();

    @Test
    void nicksActiveHereAndOnOtherServersAreShown() {
        NetworkService network = mock(NetworkService.class);
        when(network.enabled()).thenReturn(true);
        when(network.serverName()).thenReturn("lobby");
        when(network.player(elsewhere)).thenReturn(Optional.of(new NetworkPlayer(elsewhere, "Bob", "survival", Instant.now())));
        when(network.player(offline)).thenReturn(Optional.empty());
        when(network.player(here)).thenReturn(Optional.of(new NetworkPlayer(here, "Notch", "lobby", Instant.now())));

        NickRuntimeState state = new NickRuntimeState();
        state.activate(here, new NickRuntimeState.Active(nick(here, "Admin", "Notch"), null, null, false));
        state.store(nick(elsewhere, "Moderator", "Bob"));
        state.store(nick(offline, "Helper", "Alice"));
        NickView view = new NickView(state, network);

        assertEquals(Optional.of(new Nicks.Nick(here, "Notch", "Admin", null, "lobby")), view.nick(here));
        assertEquals("survival", view.nick(elsewhere).orElseThrow().server());
        assertTrue(view.nick(offline).isEmpty(), "an offline player's stored nick is not shown anywhere");
        assertEquals(elsewhere, view.byName("bob").orElseThrow().playerId());
        assertTrue(view.byName("Alice").isEmpty());
        assertTrue(view.byName("Admin").isEmpty(), "only nicks, not the real names behind them");
        assertEquals(List.of("Notch", "Bob"), view.active().stream().map(Nicks.Nick::name).toList());
    }

    @Test
    void withoutNetworkModeOnlyTheNicksOnThisServerCount() {
        NetworkService network = mock(NetworkService.class);
        when(network.enabled()).thenReturn(false);
        NickRuntimeState state = new NickRuntimeState();
        state.activate(here, new NickRuntimeState.Active(nick(here, "Admin", "Notch"), null, null, false));
        state.store(nick(elsewhere, "Moderator", "Bob"));
        NickView view = new NickView(state, network);

        assertEquals(Optional.of(new Nicks.Nick(here, "Notch", "Admin", null, null)), view.nick(here));
        assertTrue(view.nick(elsewhere).isEmpty());
        assertEquals(1, view.active().size());
    }

    @Test
    void aReloadKeepsTheNicksActiveHere() {
        NickRuntimeState state = new NickRuntimeState();
        state.activate(here, new NickRuntimeState.Active(nick(here, "Admin", "Notch"), null, null, false));
        state.store(nick(offline, "Helper", "Alice"));

        state.replaceStored(List.of(nick(elsewhere, "Moderator", "Bob")));

        assertTrue(state.stored(here).isPresent());
        assertTrue(state.stored(elsewhere).isPresent());
        assertTrue(state.stored(offline).isEmpty());
    }

    private static NickRecord nick(UUID id, String realName, String name) {
        return new NickRecord(id, realName, name, null, Instant.now());
    }
}

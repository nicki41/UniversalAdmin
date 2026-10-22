package dev.universaladmin.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.bukkit.permissions.Permissible;
import org.junit.jupiter.api.Test;

/** Vanished players are left out of what the network shows - except for those who see vanished players. */
class NetworkVisibilityTest {

    private static final UUID STEVE = UUID.randomUUID();
    private static final UUID ALEX = UUID.randomUUID();

    @Test
    void vanishedPlayersAreHiddenFromViewersWithoutTheBypassNode() {
        NetworkService network = mock(NetworkService.class);
        NetworkPlayer steve = new NetworkPlayer(STEVE, "Steve", "survival", Instant.now());
        NetworkPlayer alex = new NetworkPlayer(ALEX, "Alex", "lobby", Instant.now());
        NetworkPlayer elsewhere = new NetworkPlayer(null, "Guest", "minigames", null);
        when(network.players()).thenReturn(List.of(alex, elsewhere, steve));
        when(network.vanished(STEVE)).thenReturn(true);
        Permissible player = mock(Permissible.class);
        Permissible staff = mock(Permissible.class);
        when(staff.hasPermission(NetworkVisibility.SEE_VANISHED.value())).thenReturn(true);

        assertEquals(List.of(alex, elsewhere), NetworkVisibility.players(player, network));
        assertEquals(List.of(alex, elsewhere, steve), NetworkVisibility.players(staff, network));
        assertTrue(NetworkVisibility.canSee(player, network, elsewhere));
    }
}

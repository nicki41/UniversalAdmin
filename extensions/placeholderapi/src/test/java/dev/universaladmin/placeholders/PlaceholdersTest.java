package dev.universaladmin.placeholders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.universaladmin.network.NetworkPlayer;
import dev.universaladmin.network.NetworkServer;
import dev.universaladmin.network.NetworkService;
import dev.universaladmin.status.MaintenanceStatus;
import dev.universaladmin.status.PlayerStatus;
import dev.universaladmin.status.ReportOverview;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PlaceholdersTest {

    private static final UUID STEVE = UUID.randomUUID();
    private static final UUID ALEX = UUID.randomUUID();
    private static final UUID MOD = UUID.randomUUID();

    @Test
    void playerPlaceholders() {
        Placeholders placeholders = new Placeholders(new Fake(network(false, List.of(), List.of()), true));

        assertEquals("yes", placeholders.resolve(STEVE, "vanished"));
        assertEquals("no", placeholders.resolve(ALEX, "vanished"));
        assertEquals("yes", placeholders.resolve(STEVE, "muted"));
        assertEquals("spam", placeholders.resolve(STEVE, "mute_reason"));
        assertTrue(placeholders.resolve(STEVE, "mute_remaining").matches("1h|59m 59s"), placeholders.resolve(STEVE, "mute_remaining"));
        assertEquals("permanent", placeholders.resolve(ALEX, "mute_remaining"));
        assertEquals("", placeholders.resolve(MOD, "mute_remaining"));
        assertEquals("2", placeholders.resolve(STEVE, "warnings"));
        assertEquals("7", placeholders.resolve(STEVE, "punishments"));
        assertEquals("yes", placeholders.resolve(MOD, "STAFFMODE")); // case does not matter
        assertEquals("", placeholders.resolve(null, "vanished")); // no player, e.g. a hologram
    }

    @Test
    void serverPlaceholdersLeaveVanishedPlayersOut() {
        Placeholders placeholders = new Placeholders(new Fake(network(false, List.of(), List.of()), true));

        assertEquals("2", placeholders.resolve(null, "online")); // Steve is vanished
        assertEquals("1", placeholders.resolve(null, "staff_online"));
        assertEquals("1", placeholders.resolve(null, "vanished_count"));
        assertEquals("4", placeholders.resolve(null, "open_reports"));
        assertEquals("no", placeholders.resolve(null, "maintenance"));
        assertEquals("", placeholders.resolve(null, "server"));
        assertEquals("2", placeholders.resolve(null, "network_online")); // without network mode: like "online"
        assertEquals("1", placeholders.resolve(null, "network_servers"));
        assertNull(placeholders.resolve(null, "no_such_placeholder"));
    }

    @Test
    void networkPlaceholders() {
        List<NetworkServer> servers = List.of(
                new NetworkServer("lobby", true, true, 12, 100, 20, "1.21.11", Instant.now()),
                new NetworkServer("survival", true, true, 30, 100, 19.5, "1.21.11", Instant.now()),
                new NetworkServer("event", false, false, 0, 50, 0, "?", Instant.now()));
        List<NetworkPlayer> players = List.of(
                new NetworkPlayer(STEVE, "Steve", "lobby", Instant.now()),
                new NetworkPlayer(ALEX, "Alex", "survival", Instant.now()));
        Placeholders placeholders = new Placeholders(new Fake(network(true, servers, players), true));

        assertEquals("lobby", placeholders.resolve(null, "server"));
        assertEquals("1", placeholders.resolve(null, "network_online")); // Alex is vanished on survival
        assertEquals("2", placeholders.resolve(null, "network_servers"));
        assertEquals("30", placeholders.resolve(null, "network_online_survival"));
        assertEquals("0", placeholders.resolve(null, "network_online_event"));
        assertEquals("", placeholders.resolve(null, "network_online_nowhere"));
    }

    @Test
    void emptyWhenAModuleIsOff() {
        Placeholders placeholders = new Placeholders(new Fake(network(false, List.of(), List.of()), false));

        assertEquals("", placeholders.resolve(STEVE, "vanished"));
        assertEquals("", placeholders.resolve(null, "open_reports"));
        assertEquals("3", placeholders.resolve(null, "online")); // without vanish info nobody is hidden
    }

    @Test
    void comparesApiVersions() {
        assertTrue(PlaceholdersPlugin.compare("1.2", "1.2") == 0);
        assertTrue(PlaceholdersPlugin.compare("1.10", "1.2") > 0);
        assertTrue(PlaceholdersPlugin.compare("1.1", "1.2") < 0);
    }

    private static NetworkService network(boolean enabled, List<NetworkServer> servers, List<NetworkPlayer> players) {
        return (NetworkService) Proxy.newProxyInstance(PlaceholdersTest.class.getClassLoader(), new Class<?>[] {NetworkService.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "enabled" -> enabled;
                    case "serverName" -> enabled ? "lobby" : "";
                    case "servers" -> servers;
                    case "players" -> players;
                    case "vanished" -> ALEX.equals(args[0]);
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private record Fake(NetworkService network, boolean modules) implements Placeholders.Environment {

        @Override
        public Optional<PlayerStatus> playerStatus() {
            return modules ? Optional.of(new FakeStatus()) : Optional.empty();
        }

        @Override
        public Optional<ReportOverview> reports() {
            return modules ? Optional.of(() -> 4) : Optional.empty();
        }

        @Override
        public Optional<MaintenanceStatus> maintenance() {
            return modules ? Optional.of(() -> false) : Optional.empty();
        }

        @Override
        public List<Placeholders.OnlinePlayer> online() {
            return List.of(new Placeholders.OnlinePlayer(STEVE, true), new Placeholders.OnlinePlayer(ALEX, false),
                    new Placeholders.OnlinePlayer(MOD, true));
        }

        @Override
        public String yes() {
            return "yes";
        }

        @Override
        public String no() {
            return "no";
        }

        @Override
        public String permanent() {
            return "permanent";
        }
    }

    private static final class FakeStatus implements PlayerStatus {

        private final Map<UUID, Mute> mutes = Map.of(
                STEVE, new Mute("spam", Instant.now().plus(1, ChronoUnit.HOURS)),
                ALEX, new Mute(null, null));

        @Override
        public boolean vanished(UUID playerId) {
            return playerId.equals(STEVE);
        }

        @Override
        public boolean staffMode(UUID playerId) {
            return playerId.equals(MOD);
        }

        @Override
        public boolean frozen(UUID playerId) {
            return false;
        }

        @Override
        public boolean godmode(UUID playerId) {
            return false;
        }

        @Override
        public Optional<Mute> mute(UUID playerId) {
            return Optional.ofNullable(mutes.get(playerId));
        }

        @Override
        public int activeWarnings(UUID playerId) {
            return playerId.equals(STEVE) ? 2 : 0;
        }

        @Override
        public int punishments(UUID playerId) {
            return playerId.equals(STEVE) ? 7 : 0;
        }

        @Override
        public int vanishedOnline() {
            return Set.of(STEVE).size();
        }
    }
}

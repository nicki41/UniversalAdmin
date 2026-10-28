package dev.universaladmin.core;

import dev.universaladmin.status.Nicks;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

/**
 * Best-known name for a player UUID, for display and audit texts - so the
 * audit log says "Banned Steve" instead of "Banned 069a79f4-...". Uses the
 * server's own player cache plus the names UniversalAdmin resolved itself
 * recently ({@link #remember}) - in network mode that covers players who
 * only ever played on another server. Never a network or database lookup,
 * so it is cheap and safe on any thread; falls back to the UUID itself for a
 * player nobody has seen, and when no server is running at all (unit tests).
 *
 * <p>Always the real name: a nicked player ({@link Nicks}) is named by their
 * own name here, not by the nick everyone else sees.
 */
public final class PlayerNames {

    private static final int REMEMBERED = 1024;

    private static final Map<UUID, String> RECENT = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<UUID, String> eldest) {
            return size() > REMEMBERED;
        }
    };

    private PlayerNames() {
    }

    public static String of(UUID playerId) {
        if (playerId == null) {
            return "-";
        }
        Optional<Nicks.Nick> nick = nick(playerId);
        if (nick.isPresent()) {
            return nick.get().realName();
        }
        synchronized (RECENT) {
            String remembered = RECENT.get(playerId);
            if (remembered != null) {
                return remembered;
            }
        }
        if (Bukkit.getServer() == null) {
            return playerId.toString();
        }
        OfflinePlayer player = Bukkit.getOfflinePlayer(playerId);
        String name = player.getName();
        return name != null ? name : playerId.toString();
    }

    /**
     * The player's own name - {@code Player#getName()} returns the nick while
     * they are nicked. What to record (punishments, notes, profiles) and to
     * act under; what other players see is {@code getName()}.
     *
     * @since 1.6
     */
    public static String real(Player player) {
        return real(player.getUniqueId(), player.getName());
    }

    /**
     * The real name of the player {@code playerId}, if {@code name} is a nick
     * they are shown under right now; {@code name} itself otherwise.
     *
     * @since 1.6
     */
    public static String real(UUID playerId, String name) {
        if (playerId == null) {
            return name;
        }
        return nick(playerId).map(Nicks.Nick::realName).orElse(name);
    }

    /**
     * The real name behind the nick of {@code playerId}, for a viewer who
     * may see it ({@link Nicks#SEE_REAL_NAME}) - for menus that list
     * players. Empty if the player is not nicked or the viewer may not know.
     *
     * @since 1.6
     */
    public static Optional<String> revealedTo(Player viewer, UUID playerId) {
        if (!viewer.hasPermission(Nicks.SEE_REAL_NAME.value())) {
            return Optional.empty();
        }
        return nick(playerId).map(Nicks.Nick::realName);
    }

    /** Remembers a name UniversalAdmin resolved some other way (another server, the shared player profiles). */
    public static void remember(UUID playerId, String name) {
        if (playerId == null || name == null || name.isBlank()) {
            return;
        }
        if (nick(playerId).filter(nick -> nick.name().equalsIgnoreCase(name)).isPresent()) {
            // The network shows nicked players under their nick - not a name to keep.
            return;
        }
        synchronized (RECENT) {
            RECENT.put(playerId, name);
        }
    }

    private static Optional<Nicks.Nick> nick(UUID playerId) {
        return UniversalAdminProvider.find()
                .flatMap(platform -> platform.services().get(Nicks.class))
                .flatMap(nicks -> nicks.nick(playerId));
    }
}

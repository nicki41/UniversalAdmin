package dev.universaladmin.core;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

/**
 * Best-known name for a player UUID, for display and audit texts - so the
 * audit log says "Banned Steve" instead of "Banned 069a79f4-...". Uses the
 * server's own player cache plus the names UniversalAdmin resolved itself
 * recently ({@link #remember}) - in network mode that covers players who
 * only ever played on another server. Never a network or database lookup,
 * so it is cheap and safe on any thread; falls back to the UUID itself for a
 * player nobody has seen, and when no server is running at all (unit tests).
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

    /** Remembers a name UniversalAdmin resolved some other way (another server, the shared player profiles). */
    public static void remember(UUID playerId, String name) {
        if (playerId == null || name == null || name.isBlank()) {
            return;
        }
        synchronized (RECENT) {
            RECENT.put(playerId, name);
        }
    }
}

package dev.universaladmin.core;

import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

/**
 * Best-known name for a player UUID, for display and audit texts - so the
 * audit log says "Banned Steve" instead of "Banned 069a79f4-...". Uses only
 * the server's own player cache (never a network lookup), so it is cheap and
 * safe on any thread; falls back to the UUID itself for a player the server
 * has never seen, and when no server is running at all (unit tests).
 */
public final class PlayerNames {

    private PlayerNames() {
    }

    public static String of(UUID playerId) {
        if (playerId == null) {
            return "-";
        }
        if (Bukkit.getServer() == null) {
            return playerId.toString();
        }
        OfflinePlayer player = Bukkit.getOfflinePlayer(playerId);
        String name = player.getName();
        return name != null ? name : playerId.toString();
    }
}

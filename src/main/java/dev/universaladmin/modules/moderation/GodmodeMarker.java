package dev.universaladmin.modules.moderation;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

/**
 * Godmode as a mark in the player's own data ({@code universaladmin:godmode}
 * in the persistent data container), which Minecraft saves with the
 * player. {@code Entity#isInvulnerable()} cannot tell godmode apart from the
 * few seconds of protection after joining or a hit - on Paper 26 it reports
 * both - so it is not read to decide whether godmode is on.
 */
public final class GodmodeMarker {

    static final NamespacedKey KEY = NamespacedKey.fromString("universaladmin:godmode");

    private GodmodeMarker() {
    }

    public static boolean marked(Player player) {
        return player.getPersistentDataContainer().has(KEY, PersistentDataType.BYTE);
    }

    public static void mark(Player player, boolean godmode) {
        if (godmode) {
            player.getPersistentDataContainer().set(KEY, PersistentDataType.BYTE, (byte) 1);
        } else {
            player.getPersistentDataContainer().remove(KEY);
        }
    }
}

package dev.universaladmin.modules.players.gui;

import dev.universaladmin.gui.GuiItem;
import dev.universaladmin.localization.ComponentMessages;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.localization.MessageService;
import dev.universaladmin.modules.players.PlayerProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/** Row rendering shared by the Online/Offline/Recently-Seen list pages. */
final class PlayerGuiItems {

    private PlayerGuiItems() {
    }

    static GuiItem onlinePlayerItem(MessageService messages, Player player) {
        return onlinePlayerItem(messages, player, null);
    }

    /** @param server this server's name in network mode, {@code null} otherwise */
    static GuiItem onlinePlayerItem(MessageService messages, Player player, String server) {
        List<Component> lore = new ArrayList<>();
        if (server != null) {
            lore.add(line(messages, "players.gui.row.server", server));
        }
        lore.add(line(messages, "players.gui.row.world", player.getWorld().getName()));
        lore.add(line(messages, "players.gui.row.gamemode", player.getGameMode()));
        lore.add(line(messages, "players.gui.row.ping", player.getPing()));
        return GuiItem.playerHead(player, Component.text(player.getName(), NamedTextColor.GREEN), lore);
    }

    /** A player on another server of the network. */
    static GuiItem networkPlayerItem(MessageService messages, UUID id, String name, String server) {
        List<Component> lore = List.of(
                line(messages, "players.gui.row.server", server),
                line(messages, "players.gui.row.elsewhere", server));
        return GuiItem.playerHead(Bukkit.getOfflinePlayer(id), Component.text(name, NamedTextColor.AQUA), lore);
    }

    static GuiItem offlineProfileItem(MessageService messages, PlayerProfile profile) {
        List<Component> lore = List.of(
                line(messages, "players.gui.row.last-seen", PlayerGuiFormat.instant(profile.lastSeen())),
                line(messages, "players.gui.row.first-join", PlayerGuiFormat.instant(profile.firstJoin())));
        return GuiItem.playerHead(Bukkit.getOfflinePlayer(profile.id()), Component.text(profile.lastKnownName(), NamedTextColor.GRAY), lore);
    }

    private static Component line(MessageService messages, String key, Object arg) {
        return ComponentMessages.render(messages.get(MessageKey.of(key), arg)).colorIfAbsent(NamedTextColor.GRAY);
    }
}

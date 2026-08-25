package dev.universaladmin.modules.players.gui;

import dev.universaladmin.gui.GuiItem;
import dev.universaladmin.localization.ComponentMessages;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.localization.MessageService;
import dev.universaladmin.modules.players.PlayerProfile;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/** Row rendering shared by the Online/Offline/Recently-Seen list pages. */
final class PlayerGuiItems {

    private PlayerGuiItems() {
    }

    static GuiItem onlinePlayerItem(MessageService messages, Player player) {
        List<Component> lore = List.of(
                line(messages, "players.gui.row.world", player.getWorld().getName()),
                line(messages, "players.gui.row.gamemode", player.getGameMode()),
                line(messages, "players.gui.row.ping", player.getPing()));
        return GuiItem.playerHead(player, Component.text(player.getName(), NamedTextColor.GREEN), lore);
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

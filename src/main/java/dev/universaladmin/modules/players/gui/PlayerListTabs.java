package dev.universaladmin.modules.players.gui;

import dev.universaladmin.gui.GuiButton;
import dev.universaladmin.gui.GuiGlass;
import dev.universaladmin.gui.GuiItem;
import dev.universaladmin.gui.GuiPage;
import dev.universaladmin.gui.GuiView;
import dev.universaladmin.localization.ComponentMessages;
import dev.universaladmin.localization.MessageKey;
import java.util.List;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/**
 * The three player lists as tabs in the header row, either side of the
 * page's own icon: the open one green, the others grey. A tab opens its list
 * in place of the current one, so "back" still leads to where the lists were
 * opened from.
 */
final class PlayerListTabs {

    enum Tab {
        ONLINE(2, "players.gui.tabs.online", OnlinePlayersListPage::new),
        OFFLINE(3, "players.gui.tabs.offline", OfflinePlayersListPage::new),
        RECENT(5, "players.gui.tabs.recent", RecentlySeenPlayersListPage::new);

        private final int slot;
        private final String labelKey;
        private final Function<PlayerGuiContext, GuiPage> page;

        Tab(int slot, String labelKey, Function<PlayerGuiContext, GuiPage> page) {
            this.slot = slot;
            this.labelKey = labelKey;
            this.page = page;
        }
    }

    private PlayerListTabs() {
    }

    static void place(GuiView view, Player viewer, PlayerGuiContext ctx, Tab current) {
        for (Tab tab : Tab.values()) {
            Component label = text(ctx, tab.labelKey);
            if (tab == current) {
                view.place(tab.slot, GuiItem.glass(GuiGlass.LIME, label, List.of()));
            } else {
                view.place(tab.slot, GuiButton.of(GuiItem.glass(GuiGlass.GRAY, label, List.of(text(ctx, "players.gui.tabs.hint"))),
                        clickCtx -> tab.page.apply(ctx).open(clickCtx.viewer())), viewer);
            }
        }
    }

    private static Component text(PlayerGuiContext ctx, String key) {
        return ComponentMessages.render(ctx.messages().get(MessageKey.of(key)));
    }
}

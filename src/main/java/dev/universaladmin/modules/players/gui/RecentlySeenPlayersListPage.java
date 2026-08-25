package dev.universaladmin.modules.players.gui;

import dev.universaladmin.gui.AbstractListGuiPage;
import dev.universaladmin.gui.GuiClickContext;
import dev.universaladmin.gui.GuiItem;
import dev.universaladmin.gui.GuiPageId;
import dev.universaladmin.modules.players.PlayerProfile;
import dev.universaladmin.modules.players.PlayerSearchQuery;
import dev.universaladmin.modules.players.PlayerSort;
import dev.universaladmin.modules.players.PlayersSettings;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/** Profiles sorted by last-seen, newest first - fixed sort, no filter, so the plain {@link AbstractListGuiPage} fits as-is. */
public final class RecentlySeenPlayersListPage extends AbstractListGuiPage<PlayerProfile> {

    public static final GuiPageId ID = GuiPageId.core("players.recently-seen");

    private final PlayerGuiContext ctx;

    public RecentlySeenPlayersListPage(PlayerGuiContext ctx) {
        super(ID, ctx.framework(), ctx.messages(), ctx.scheduler());
        this.ctx = ctx;
    }

    @Override
    protected Component title(Player viewer) {
        return text("players.gui.recently-seen.title");
    }

    @Override
    protected CompletableFuture<List<PlayerProfile>> loadItems(Player viewer) {
        int limit = ctx.settings().get(PlayersSettings.GUI_MAX_RESULTS);
        return ctx.playerService().search(new PlayerSearchQuery(null, PlayerSort.LAST_SEEN_DESC, limit));
    }

    @Override
    protected GuiItem render(PlayerProfile profile) {
        return PlayerGuiItems.offlineProfileItem(ctx.messages(), profile);
    }

    @Override
    protected void onSelect(GuiClickContext context, PlayerProfile profile) {
        context.open(new PlayerProfilePage(ctx, profile.id(), profile.lastKnownName()));
    }
}

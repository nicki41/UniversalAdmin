package dev.universaladmin.modules.whitelist.gui;

import dev.universaladmin.gui.AbstractListGuiPage;
import dev.universaladmin.gui.GuiClickContext;
import dev.universaladmin.gui.GuiItem;
import dev.universaladmin.gui.GuiPageId;
import dev.universaladmin.modules.whitelist.WhitelistMemberView;
import dev.universaladmin.modules.whitelist.WhitelistScope;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * Every native whitelist member ({@code Bukkit.getWhitelistedPlayers()} is
 * the source of truth for membership), annotated with UniversalAdmin's own
 * metadata wherever it has any - see {@link WhitelistMemberView}. Paginated
 * via {@link AbstractListGuiPage} since a whitelist can outgrow one page,
 * unlike the small, fixed lists on {@code ServerHomePage}/{@code WorldsHomePage}.
 */
public final class WhitelistMembersListPage extends AbstractListGuiPage<WhitelistMemberView> {

    public static final GuiPageId ID = GuiPageId.core("whitelist.members");

    private final WhitelistGuiContext ctx;
    private final WhitelistScope scope;

    /** @param scope this server's whitelist or the network whitelist (all of whose entries UniversalAdmin manages) */
    public WhitelistMembersListPage(WhitelistGuiContext ctx, WhitelistScope scope) {
        super(ID, ctx.framework(), ctx.messages(), ctx.scheduler());
        this.ctx = ctx;
        this.scope = scope;
    }

    @Override
    protected Component title(Player viewer) {
        return text(scope == WhitelistScope.NETWORK ? "whitelist.gui.members.network-title" : "whitelist.gui.members.title");
    }

    @Override
    protected CompletableFuture<List<WhitelistMemberView>> loadItems(Player viewer) {
        return ctx.service(scope).listMembers();
    }

    @Override
    protected GuiItem render(WhitelistMemberView member) {
        Component managedLine = text(member.managedByUniversalAdmin() ? "whitelist.gui.members.managed" : "whitelist.gui.members.external");
        Component name = Component.text(member.playerName(), NamedTextColor.GOLD);
        if (member.online()) {
            return GuiItem.playerHead(Bukkit.getOfflinePlayer(member.playerId()), name, List.of(managedLine));
        }
        return GuiItem.of(Material.SKELETON_SKULL, name, List.of(managedLine));
    }

    @Override
    protected void onSelect(GuiClickContext context, WhitelistMemberView member) {
        context.open(new WhitelistMemberDetailPage(ctx, member, scope));
    }
}

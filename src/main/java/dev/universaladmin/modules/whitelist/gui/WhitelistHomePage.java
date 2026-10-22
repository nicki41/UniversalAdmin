package dev.universaladmin.modules.whitelist.gui;

import dev.universaladmin.gui.AbstractGuiPage;
import dev.universaladmin.gui.GuiButton;
import dev.universaladmin.gui.GuiItem;
import dev.universaladmin.gui.GuiLayout;
import dev.universaladmin.gui.GuiPageId;
import dev.universaladmin.gui.GuiRenderContext;
import dev.universaladmin.gui.GuiView;
import dev.universaladmin.modules.whitelist.WhitelistPermissions;
import dev.universaladmin.modules.whitelist.WhitelistScope;
import dev.universaladmin.modules.whitelist.action.WhitelistActionIds;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * {@code whitelist.home} - status tile + toggle, and navigation into the
 * Members list and the Add wizard, registered by {@code WhitelistModule} in
 * place of the {@code PlaceholderGuiPage} {@code UniversalAdminPlugin} used
 * to register for this id.
 */
public final class WhitelistHomePage extends AbstractGuiPage {

    public static final GuiPageId ID = GuiPageId.core("whitelist.home");

    private final WhitelistGuiContext ctx;

    public WhitelistHomePage(WhitelistGuiContext ctx) {
        super(ID, ctx.framework(), ctx.messages());
        this.ctx = ctx;
    }

    @Override
    protected Component title(Player viewer) {
        return text("whitelist.gui.home.title");
    }

    @Override
    protected void renderContent(GuiRenderContext context) {
        GuiView view = context.view();
        Player viewer = context.viewer();
        boolean enabled = Bukkit.hasWhitelist();

        view.place(GuiLayout.CONTENT_START_SLOT, GuiItem.of(enabled ? Material.LIME_WOOL : Material.RED_WOOL,
                text(enabled ? "whitelist.gui.home.status-on" : "whitelist.gui.home.status-off")));

        view.place(GuiLayout.CONTENT_START_SLOT + 1, new GuiButton(
                GuiItem.of(enabled ? Material.RED_DYE : Material.LIME_DYE,
                        text(enabled ? "whitelist.gui.home.disable" : "whitelist.gui.home.enable")),
                WhitelistPermissions.TOGGLE,
                clickCtx -> WhitelistGuiActions.<Void>runAction(ctx, clickCtx.viewer(),
                        enabled ? WhitelistActionIds.DISABLE : WhitelistActionIds.ENABLE, null, () -> this.open(clickCtx.viewer()))),
                viewer);

        view.place(GuiLayout.CONTENT_START_SLOT + 3, new GuiButton(GuiItem.of(Material.BOOK, text("whitelist.gui.home.members"),
                List.of(text("whitelist.gui.home.members-lore"))),
                WhitelistPermissions.VIEW, clickCtx -> clickCtx.open(new WhitelistMembersListPage(ctx, WhitelistScope.SERVER))), viewer);

        view.place(GuiLayout.CONTENT_START_SLOT + 4, new GuiButton(GuiItem.of(Material.NAME_TAG, text("whitelist.gui.home.add"),
                List.of(text("whitelist.gui.home.add-lore"))),
                WhitelistPermissions.ADD, clickCtx -> clickCtx.open(new WhitelistAddPage(ctx, WhitelistScope.SERVER))), viewer);

        if (ctx.networkWhitelist().available()) {
            renderNetworkRow(view, viewer, GuiLayout.CONTENT_START_SLOT + GuiLayout.COLUMNS);
        }
    }

    /** The network whitelist: status, switch, members and add - like the row above, for every server at once. */
    private void renderNetworkRow(GuiView view, Player viewer, int slot) {
        boolean enabled = ctx.networkWhitelist().enabled();
        view.place(slot, GuiItem.of(enabled ? Material.LIME_WOOL : Material.RED_WOOL,
                text(enabled ? "whitelist.gui.home.network-status-on" : "whitelist.gui.home.network-status-off"),
                List.of(text("whitelist.gui.home.network-lore", ctx.networkWhitelist().size()))));
        view.place(slot + 1, new GuiButton(
                GuiItem.of(enabled ? Material.RED_DYE : Material.LIME_DYE,
                        text(enabled ? "whitelist.gui.home.network-disable" : "whitelist.gui.home.network-enable")),
                WhitelistPermissions.NETWORK,
                clickCtx -> WhitelistGuiActions.<Void>runAction(ctx, clickCtx.viewer(),
                        enabled ? WhitelistActionIds.NETWORK_DISABLE : WhitelistActionIds.NETWORK_ENABLE, null,
                        () -> this.open(clickCtx.viewer()))),
                viewer);
        view.place(slot + 3, new GuiButton(GuiItem.of(Material.BOOK, text("whitelist.gui.home.network-members")),
                WhitelistPermissions.VIEW, clickCtx -> clickCtx.open(new WhitelistMembersListPage(ctx, WhitelistScope.NETWORK))), viewer);
        view.place(slot + 4, new GuiButton(GuiItem.of(Material.NAME_TAG, text("whitelist.gui.home.network-add")),
                WhitelistPermissions.NETWORK, clickCtx -> clickCtx.open(new WhitelistAddPage(ctx, WhitelistScope.NETWORK))), viewer);
    }
}

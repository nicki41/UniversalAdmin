package dev.universaladmin.modules.whitelist.gui;

import dev.universaladmin.gui.AbstractGuiPage;
import dev.universaladmin.gui.ConfirmationDialog;
import dev.universaladmin.gui.GuiButton;
import dev.universaladmin.gui.GuiItem;
import dev.universaladmin.gui.GuiLayout;
import dev.universaladmin.gui.GuiPageId;
import dev.universaladmin.gui.GuiRenderContext;
import dev.universaladmin.gui.GuiView;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.modules.whitelist.WhitelistEntry;
import dev.universaladmin.modules.whitelist.WhitelistMemberView;
import dev.universaladmin.modules.whitelist.WhitelistPermissions;
import dev.universaladmin.modules.whitelist.action.WhitelistActionIds;
import dev.universaladmin.settings.CoreSettings;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** Ephemeral - built fresh per click with the target member baked in, like {@code WorldProfilePage}. Never registered in {@code GuiRegistry}. */
public final class WhitelistMemberDetailPage extends AbstractGuiPage {

    public static final GuiPageId ID = GuiPageId.core("whitelist.member");

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final WhitelistGuiContext ctx;
    private final WhitelistMemberView member;

    public WhitelistMemberDetailPage(WhitelistGuiContext ctx, WhitelistMemberView member) {
        super(ID, ctx.framework(), ctx.messages());
        this.ctx = ctx;
        this.member = member;
    }

    @Override
    protected boolean refreshable() {
        return false;
    }

    @Override
    protected Component title(Player viewer) {
        return Component.text(member.playerName());
    }

    @Override
    protected void renderContent(GuiRenderContext context) {
        GuiView view = context.view();
        Player viewer = context.viewer();

        List<GuiItem> fields = new ArrayList<>();
        fields.add(header());
        Optional<WhitelistEntry> managed = member.managedEntry();
        if (managed.isPresent()) {
            WhitelistEntry entry = managed.get();
            fields.add(field("whitelist.gui.member.added-by", entry.addedByName()));
            fields.add(field("whitelist.gui.member.added-at", TIMESTAMP.format(entry.addedAt())));
            if (entry.reason() != null) {
                fields.add(field("whitelist.gui.member.reason", entry.reason()));
            }
            if (entry.notes() != null) {
                fields.add(field("whitelist.gui.member.notes", entry.notes()));
            }
            fields.add(field("whitelist.gui.member.expires",
                    entry.expiresAt() != null
                            ? TIMESTAMP.format(entry.expiresAt())
                            : messages.get(MessageKey.of("whitelist.gui.member.permanent"))));
        } else {
            fields.add(field("whitelist.gui.member.source", messages.get(MessageKey.of("whitelist.gui.members.external"))));
        }

        for (int i = 0; i < fields.size() && i < GuiLayout.contentSize() - 1; i++) {
            view.place(GuiLayout.contentSlot(i), fields.get(i));
        }

        view.place(GuiLayout.CONTENT_END_SLOT, new GuiButton(GuiItem.of(Material.BARRIER, text("whitelist.gui.member.remove")),
                WhitelistPermissions.REMOVE, clickCtx -> confirmRemove(clickCtx.viewer())), viewer);
    }

    private void confirmRemove(Player viewer) {
        // Reopens the Members list rather than "back" (ConfirmationDialog.back()
        // would replay this exact now-stale page) since the member is gone.
        Runnable apply = () -> WhitelistGuiActions.runAction(ctx, viewer, WhitelistActionIds.REMOVE, member.playerId(),
                () -> new WhitelistMembersListPage(ctx).open(viewer));
        if (!ctx.settings().get(CoreSettings.GUI_CONFIRMATIONS)) {
            apply.run();
            return;
        }
        ConfirmationDialog.open(viewer, framework, messages, text("whitelist.gui.member.remove"),
                List.of(text("whitelist.gui.member.confirm-remove", member.playerName())), ConfirmationDialog.DangerLevel.WARNING,
                confirmCtx -> apply.run(), confirmCtx -> confirmCtx.back());
    }

    private GuiItem header() {
        Component name = Component.text(member.playerName(), NamedTextColor.GOLD);
        List<Component> lore = List.of(Component.text(member.playerId().toString(), NamedTextColor.DARK_GRAY));
        if (member.online()) {
            return GuiItem.playerHead(Bukkit.getOfflinePlayer(member.playerId()), name, lore);
        }
        return GuiItem.of(Material.SKELETON_SKULL, name, lore);
    }

    private GuiItem field(String labelKey, String value) {
        return GuiItem.of(Material.PAPER, text(labelKey), List.of(Component.text(value, NamedTextColor.GRAY)));
    }
}

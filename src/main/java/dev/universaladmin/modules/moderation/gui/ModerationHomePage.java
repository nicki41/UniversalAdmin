package dev.universaladmin.modules.moderation.gui;

import dev.universaladmin.action.ActionId;
import dev.universaladmin.gui.AbstractGuiPage;
import dev.universaladmin.gui.GuiButton;
import dev.universaladmin.gui.GuiItem;
import dev.universaladmin.gui.GuiLayout;
import dev.universaladmin.gui.GuiPageId;
import dev.universaladmin.gui.GuiRenderContext;
import dev.universaladmin.gui.GuiView;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.modules.moderation.ModerationPermissions;
import dev.universaladmin.modules.moderation.action.ModerationActionIds;
import dev.universaladmin.permission.PermissionNode;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * The Moderation module's main page: the punishment lists (Active/Recent/
 * Warnings/Bans/Mutes/Frozen), each just a button into a
 * differently-configured {@link PunishmentListPage}, plus the self-directed
 * staff toggles (Vanish/Godmode/No-Collision/Staff Mode) - those target the
 * clicking player themselves, architecturally different from "punish
 * another player", so they live here rather than in {@link
 * ModeratePlayerPage}'s target wizard. Registered under {@code
 * core:moderation.home}, replacing the {@code PlaceholderGuiPage} {@code
 * UniversalAdminPlugin.registerMainMenu} put there - see {@code
 * ModerationModule}.
 */
public final class ModerationHomePage extends AbstractGuiPage {

    public static final GuiPageId ID = GuiPageId.core("moderation.home");

    private final ModerationGuiContext ctx;

    public ModerationHomePage(ModerationGuiContext ctx) {
        super(ID, ctx.framework(), ctx.messages());
        this.ctx = ctx;
    }

    @Override
    protected Component title(Player viewer) {
        return text("moderation.gui.home.title");
    }

    @Override
    protected void renderContent(GuiRenderContext context) {
        GuiView view = context.view();
        Player viewer = context.viewer();
        UUID id = viewer.getUniqueId();
        int slot = GuiLayout.CONTENT_START_SLOT;

        view.place(slot++, new GuiButton(GuiItem.of(Material.REDSTONE_TORCH, text("moderation.gui.home.active"),
                List.of(text("moderation.gui.home.active-lore"))), ModerationPermissions.VIEW,
                clickCtx -> clickCtx.open(PunishmentListPage.active(ctx))), viewer);
        view.place(slot++, new GuiButton(GuiItem.of(Material.WRITTEN_BOOK, text("moderation.gui.home.recent"),
                List.of(text("moderation.gui.home.recent-lore"))), ModerationPermissions.VIEW,
                clickCtx -> clickCtx.open(PunishmentListPage.recent(ctx))), viewer);
        view.place(slot++, new GuiButton(GuiItem.of(Material.BOOK, text("moderation.gui.home.warnings"),
                List.of(text("moderation.gui.home.warnings-lore"))), ModerationPermissions.VIEW,
                clickCtx -> clickCtx.open(PunishmentListPage.warnings(ctx))), viewer);
        view.place(slot++, new GuiButton(GuiItem.of(Material.BARRIER, text("moderation.gui.home.bans"),
                List.of(text("moderation.gui.home.bans-lore"))), ModerationPermissions.VIEW,
                clickCtx -> clickCtx.open(PunishmentListPage.bans(ctx))), viewer);
        view.place(slot++, new GuiButton(GuiItem.of(Material.NOTE_BLOCK, text("moderation.gui.home.mutes"),
                List.of(text("moderation.gui.home.mutes-lore"))), ModerationPermissions.VIEW,
                clickCtx -> clickCtx.open(PunishmentListPage.mutes(ctx))), viewer);
        view.place(slot++, new GuiButton(GuiItem.of(Material.PACKED_ICE, text("moderation.gui.home.frozen"),
                List.of(text("moderation.gui.home.frozen-lore"))), ModerationPermissions.VIEW,
                clickCtx -> clickCtx.open(PunishmentListPage.frozen(ctx))), viewer);

        view.place(slot++, toggleButton(Material.ENDER_EYE, "moderation.gui.home.vanish", ModerationPermissions.VANISH,
                ctx.vanishService().isVanished(id), ModerationActionIds.VANISH), viewer);
        view.place(slot++, toggleButton(Material.GOLDEN_APPLE, "moderation.gui.home.godmode", ModerationPermissions.GODMODE,
                ctx.godmodeState().isEnabled(id), ModerationActionIds.GODMODE), viewer);
        view.place(slot++, toggleButton(Material.SLIME_BALL, "moderation.gui.home.collision", ModerationPermissions.COLLISION,
                ctx.collisionState().isManuallyDisabled(id), ModerationActionIds.NO_COLLISION), viewer);

        boolean staffModeActive = ctx.staffModeState().isActive(id);
        view.place(slot, new GuiButton(GuiItem.of(Material.NETHER_STAR,
                text(staffModeActive ? "moderation.gui.home.staffmode-exit" : "moderation.gui.home.staffmode-enter")),
                ModerationPermissions.STAFFMODE,
                clickCtx -> ModerationGuiActions.<Void>runAction(ctx, clickCtx.viewer(),
                        staffModeActive ? ModerationActionIds.STAFF_MODE_EXIT : ModerationActionIds.STAFF_MODE_ENTER,
                        null, () -> this.open(clickCtx.viewer()))), viewer);
    }

    private GuiButton toggleButton(Material material, String labelKey, PermissionNode permission, boolean currentlyOn, ActionId actionId) {
        String status = messages.get(MessageKey.of(currentlyOn ? "moderation.gui.status.on" : "moderation.gui.status.off"));
        return new GuiButton(GuiItem.of(material, text(labelKey, status)), permission,
                clickCtx -> ModerationGuiActions.<Void>runAction(ctx, clickCtx.viewer(), actionId, null, () -> this.open(clickCtx.viewer())));
    }
}

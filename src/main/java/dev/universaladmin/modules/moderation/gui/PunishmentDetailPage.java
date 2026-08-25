package dev.universaladmin.modules.moderation.gui;

import dev.universaladmin.action.ActionId;
import dev.universaladmin.gui.AbstractGuiPage;
import dev.universaladmin.gui.GuiButton;
import dev.universaladmin.gui.GuiClickContext;
import dev.universaladmin.gui.GuiItem;
import dev.universaladmin.gui.GuiPageId;
import dev.universaladmin.gui.GuiRenderContext;
import dev.universaladmin.gui.GuiView;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.modules.moderation.ModerationFormat;
import dev.universaladmin.modules.moderation.ModerationPermissions;
import dev.universaladmin.modules.moderation.Punishment;
import dev.universaladmin.modules.moderation.action.ModerationActionIds;
import dev.universaladmin.modules.moderation.action.RemoveWarnInput;
import dev.universaladmin.modules.moderation.action.UnbanInput;
import dev.universaladmin.modules.moderation.action.UnfreezeInput;
import dev.universaladmin.modules.moderation.action.UnmuteInput;
import dev.universaladmin.permission.PermissionNode;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * Full detail of one {@link Punishment} plus, if it's still active, a
 * revoke button (Unban/Unmute/Remove Warning) gated on the matching
 * permission - ephemeral, built fresh per click with the punishment baked
 * in via the constructor, never registered in {@code GuiRegistry} (same
 * pattern as {@code AuditLogDetailPage}).
 */
public final class PunishmentDetailPage extends AbstractGuiPage {

    public static final GuiPageId ID = GuiPageId.core("moderation.detail");

    private static final int INFO_SLOT = 13;
    private static final int REVOKE_SLOT = 16;

    private final ModerationGuiContext ctx;
    private final Punishment punishment;

    public PunishmentDetailPage(ModerationGuiContext ctx, Punishment punishment) {
        super(ID, ctx.framework(), ctx.messages(), 3);
        this.ctx = ctx;
        this.punishment = punishment;
    }

    @Override
    protected Component title(Player viewer) {
        return text("moderation.gui.detail.title");
    }

    @Override
    protected boolean refreshable() {
        return false;
    }

    @Override
    protected void renderContent(GuiRenderContext context) {
        GuiView view = context.view();
        Player viewer = context.viewer();
        view.place(INFO_SLOT, GuiItem.of(Material.PAPER, Component.text(punishment.type().name(), NamedTextColor.GOLD), detailLore()));

        RevokeAction revoke = revokeActionFor();
        if (revoke != null && viewer.hasPermission(revoke.permission.value())) {
            view.place(REVOKE_SLOT,
                    GuiButton.of(GuiItem.of(Material.LIME_WOOL, text(revoke.labelKey)), this::revoke), viewer);
        }
    }

    private List<Component> detailLore() {
        String reason = punishment.reason() == null || punishment.reason().isBlank()
                ? messages.get(MessageKey.of("common.none"))
                : punishment.reason();
        return List.of(
                text("moderation.gui.detail.field-target", punishment.targetLastKnownName()),
                text("moderation.gui.detail.field-reason", reason),
                text("moderation.gui.detail.field-actor", punishment.actorName()),
                text("moderation.gui.detail.field-created", ModerationFormat.instant(punishment.createdAt(), ctx.settings())),
                text("moderation.gui.detail.field-expires", ModerationFormat.expiry(punishment.expiresAt(), ctx.settings(), messages)),
                punishment.active() ? text("moderation.gui.detail.status-active") : text("moderation.gui.detail.status-inactive"),
                punishment.revokedAt() == null
                        ? Component.empty()
                        : text("moderation.gui.detail.revoked", ModerationFormat.instant(punishment.revokedAt(), ctx.settings()), punishment.revokedBy()));
    }

    private void revoke(GuiClickContext clickCtx) {
        RevokeAction revoke = revokeActionFor();
        if (revoke == null) {
            return;
        }
        Player viewer = clickCtx.viewer();
        ModerationGuiActions.runAction(ctx, viewer, revoke.actionId, revoke.input, () -> clickCtx.back());
    }

    private RevokeAction revokeActionFor() {
        if (!punishment.active()) {
            return null;
        }
        return switch (punishment.type()) {
            case BAN, TEMP_BAN, IP_BAN -> new RevokeAction(
                    ModerationActionIds.UNBAN, new UnbanInput(punishment.targetId()), ModerationPermissions.UNBAN, "moderation.gui.detail.unban");
            case MUTE, TEMP_MUTE -> new RevokeAction(
                    ModerationActionIds.UNMUTE, new UnmuteInput(punishment.targetId()), ModerationPermissions.UNMUTE, "moderation.gui.detail.unmute");
            case WARN -> new RevokeAction(
                    ModerationActionIds.REMOVE_WARN, new RemoveWarnInput(punishment.id(), punishment.targetId()),
                    ModerationPermissions.REMOVE_WARN, "moderation.gui.detail.remove-warn");
            case FREEZE -> new RevokeAction(
                    ModerationActionIds.UNFREEZE, new UnfreezeInput(punishment.targetId()),
                    ModerationPermissions.UNFREEZE, "moderation.gui.detail.unfreeze");
            case KICK -> null;
        };
    }

    private record RevokeAction(ActionId actionId, Object input, PermissionNode permission, String labelKey) {
    }
}

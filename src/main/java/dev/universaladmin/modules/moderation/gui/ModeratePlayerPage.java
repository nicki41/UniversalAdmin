package dev.universaladmin.modules.moderation.gui;

import dev.universaladmin.action.ActionId;
import dev.universaladmin.gui.AbstractGuiPage;
import dev.universaladmin.gui.ConfirmationDialog;
import dev.universaladmin.gui.GuiButton;
import dev.universaladmin.gui.GuiClickContext;
import dev.universaladmin.gui.GuiItem;
import dev.universaladmin.gui.GuiLayout;
import dev.universaladmin.gui.GuiPage;
import dev.universaladmin.gui.GuiPageId;
import dev.universaladmin.gui.GuiRenderContext;
import dev.universaladmin.gui.GuiTextInput;
import dev.universaladmin.gui.GuiView;
import dev.universaladmin.gui.SelectionDialog;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.modules.moderation.DurationParseException;
import dev.universaladmin.modules.moderation.DurationParser;
import dev.universaladmin.modules.moderation.ModerationFormat;
import dev.universaladmin.modules.moderation.ModerationPermissions;
import dev.universaladmin.modules.moderation.ModerationPlayerLink;
import dev.universaladmin.modules.moderation.ModerationSettings;
import dev.universaladmin.modules.moderation.action.BanInput;
import dev.universaladmin.modules.moderation.action.FreezeInput;
import dev.universaladmin.modules.moderation.action.IpBanInput;
import dev.universaladmin.modules.moderation.action.KickInput;
import dev.universaladmin.modules.moderation.action.ModerationActionIds;
import dev.universaladmin.modules.moderation.action.MuteInput;
import dev.universaladmin.modules.moderation.action.UnbanInput;
import dev.universaladmin.modules.moderation.action.UnfreezeInput;
import dev.universaladmin.modules.moderation.action.UnmuteInput;
import dev.universaladmin.modules.moderation.action.WarnInput;
import dev.universaladmin.permission.PermissionNode;
import dev.universaladmin.settings.CoreSettings;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * The moderation wizard entry point: Player -&gt; Moderate -&gt; Type -&gt;
 * Reason -&gt; Duration (if needed) -&gt; Confirmation -&gt; Action. Ephemeral,
 * built fresh per click with the target baked in (like {@code
 * PlayerProfilePage}), never registered in {@code GuiRegistry}, and
 * published to the Players module via {@link ModerationPlayerLink} instead
 * of a direct import (see that interface's javadoc).
 *
 * <p>Follows the one-page-with-chained-private-methods wizard shape every
 * other multi-step flow in this codebase uses (see {@code
 * PlayerEffectsPage.promptAddEffect}/{@code promptEffectDetails}) rather than
 * a chain of separately registered pages - each step is a {@link
 * SelectionDialog}/{@link GuiTextInput}/{@link ConfirmationDialog} layered on
 * top of this same page instance, and the accumulated wizard data ({@code
 * type}, {@code reason}, {@code expiresAt}) is carried purely as method
 * parameters, never stashed in {@code GuiSession} attributes (those are
 * reserved for page/filter UI state by convention).
 */
public final class ModeratePlayerPage extends AbstractGuiPage {

    public static final GuiPageId ID = GuiPageId.core("moderation.moderate-player");

    private final ModerationGuiContext ctx;
    private final UUID targetId;
    private final String targetName;

    public ModeratePlayerPage(ModerationGuiContext ctx, UUID targetId, String targetName) {
        super(ID, ctx.framework(), ctx.messages());
        this.ctx = ctx;
        this.targetId = targetId;
        this.targetName = targetName;
    }

    /** Adapter to {@link ModerationPlayerLink}'s functional shape, so {@code ModerationModule} can register a method reference. */
    public static GuiPage open(ModerationGuiContext ctx, UUID targetId, String targetName) {
        return new ModeratePlayerPage(ctx, targetId, targetName);
    }

    @Override
    protected boolean refreshable() {
        return false;
    }

    @Override
    protected Component title(Player viewer) {
        return text("moderation.gui.moderate.title", targetName);
    }

    @Override
    protected void renderContent(GuiRenderContext context) {
        GuiView view = context.view();
        Player viewer = context.viewer();
        List<GuiButton> buttons = new ArrayList<>();
        for (WizardType type : WizardType.values()) {
            buttons.add(new GuiButton(GuiItem.of(type.material, text(type.labelKey)), type.permission,
                    clickCtx -> onTypeSelected(clickCtx, type)));
        }
        buttons.add(new GuiButton(GuiItem.of(Material.WRITTEN_BOOK, text("moderation.gui.moderate.history")), ModerationPermissions.VIEW,
                clickCtx -> clickCtx.open(PunishmentListPage.forTarget(ctx, targetId))));

        int slot = GuiLayout.CONTENT_START_SLOT;
        for (GuiButton button : buttons) {
            view.place(slot++, button, viewer);
        }
    }

    private void onTypeSelected(GuiClickContext clickCtx, WizardType type) {
        Player viewer = clickCtx.viewer();
        switch (type) {
            case UNBAN -> ModerationGuiActions.runAction(ctx, viewer, ModerationActionIds.UNBAN, new UnbanInput(targetId), () -> this.open(viewer));
            case UNMUTE -> ModerationGuiActions.runAction(ctx, viewer, ModerationActionIds.UNMUTE, new UnmuteInput(targetId), () -> this.open(viewer));
            case UNFREEZE -> ModerationGuiActions.runAction(ctx, viewer, ModerationActionIds.UNFREEZE, new UnfreezeInput(targetId), () -> this.open(viewer));
            // REMOVE_WARN removes one specific warning at a time - route to
            // the target's warnings list, where PunishmentDetailPage's own
            // revoke button (gated on the same permission) does the removal.
            case REMOVE_WARN -> clickCtx.open(PunishmentListPage.warningsForTarget(ctx, targetId));
            default -> promptReason(viewer, type);
        }
    }

    private void promptReason(Player viewer, WizardType type) {
        String customLabel = messages.get(MessageKey.of("moderation.gui.moderate.custom"));
        List<String> options = new ArrayList<>(ctx.settings().get(ModerationSettings.REASON_PRESETS));
        options.add(customLabel);
        SelectionDialog.open(viewer, framework, messages, ctx.scheduler(), text("moderation.gui.moderate.select-reason"), options,
                reason -> GuiItem.of(Material.PAPER, Component.text(reason)),
                (clickCtx, reason) -> {
                    if (reason.equals(customLabel)) {
                        promptCustomReason(viewer, type);
                    } else {
                        afterReason(viewer, type, reason);
                    }
                });
    }

    private void promptCustomReason(Player viewer, WizardType type) {
        GuiTextInput.request(viewer, text("moderation.gui.moderate.title", targetName), text("moderation.gui.moderate.prompt-reason"), "",
                text("gui.confirm"), text("gui.cancel"),
                submitted -> {
                    if (submitted == null || submitted.isBlank()) {
                        ModerationGuiActions.notifyError(viewer, messages);
                        this.open(viewer);
                        return;
                    }
                    afterReason(viewer, type, submitted.trim());
                },
                () -> this.open(viewer));
    }

    private void afterReason(Player viewer, WizardType type, String reason) {
        if (type.needsDuration) {
            promptDuration(viewer, type, reason);
        } else {
            promptConfirm(viewer, type, reason, null);
        }
    }

    private void promptDuration(Player viewer, WizardType type, String reason) {
        List<String> options = new ArrayList<>(ctx.settings().get(ModerationSettings.DURATION_PRESETS));
        String customLabel = messages.get(MessageKey.of("moderation.gui.moderate.custom"));
        options.add(customLabel);
        SelectionDialog.open(viewer, framework, messages, ctx.scheduler(), text("moderation.gui.moderate.select-duration"), options,
                duration -> GuiItem.of(Material.CLOCK, Component.text(duration)),
                (clickCtx, duration) -> {
                    if (duration.equals(customLabel)) {
                        promptCustomDuration(viewer, type, reason);
                    } else {
                        applyDuration(viewer, type, reason, duration);
                    }
                });
    }

    private void promptCustomDuration(Player viewer, WizardType type, String reason) {
        GuiTextInput.request(viewer, text("moderation.gui.moderate.title", targetName), text("moderation.gui.moderate.prompt-duration"), "",
                text("gui.confirm"), text("gui.cancel"),
                submitted -> applyDuration(viewer, type, reason, submitted),
                () -> this.open(viewer));
    }

    private void applyDuration(Player viewer, WizardType type, String reason, String durationText) {
        try {
            Instant expiresAt = DurationParser.parse(durationText).map(Instant.now()::plus).orElse(null);
            promptConfirm(viewer, type, reason, expiresAt);
        } catch (DurationParseException e) {
            ModerationGuiActions.notifyError(viewer, messages);
            this.open(viewer);
        }
    }

    private void promptConfirm(Player viewer, WizardType type, String reason, Instant expiresAt) {
        if (!ctx.settings().get(CoreSettings.GUI_CONFIRMATIONS)) {
            apply(viewer, type, reason, expiresAt);
            return;
        }
        List<Component> description = new ArrayList<>();
        description.add(text("moderation.gui.moderate.confirm-target", targetName));
        description.add(text("moderation.gui.moderate.confirm-reason", reason));
        if (type.needsDuration) {
            description.add(text("moderation.gui.moderate.confirm-expires", ModerationFormat.expiry(expiresAt, ctx.settings(), messages)));
        }
        ConfirmationDialog.open(viewer, framework, messages, text(type.labelKey), description, type.dangerLevel,
                confirmCtx -> apply(confirmCtx.viewer(), type, reason, expiresAt),
                confirmCtx -> confirmCtx.back());
    }

    private void apply(Player viewer, WizardType type, String reason, Instant expiresAt) {
        ActionId actionId = type.actionId;
        Object input = switch (type) {
            case KICK -> new KickInput(targetId, reason);
            case BAN -> new BanInput(targetId, reason, null);
            case TEMP_BAN -> new BanInput(targetId, reason, expiresAt);
            case IP_BAN -> new IpBanInput(targetId, reason, expiresAt);
            case MUTE -> new MuteInput(targetId, reason, null);
            case TEMP_MUTE -> new MuteInput(targetId, reason, expiresAt);
            case WARN -> new WarnInput(targetId, reason);
            case FREEZE -> new FreezeInput(targetId, reason);
            case UNBAN, UNMUTE, UNFREEZE, REMOVE_WARN -> throw new IllegalStateException("Unreachable: " + type);
        };
        ModerationGuiActions.runAction(ctx, viewer, actionId, input, () -> this.open(viewer));
    }

    /** One entry per wizard type; REMOVE_WARN is handled entirely by {@link #onTypeSelected} routing to a picker instead. */
    private enum WizardType {
        KICK(Material.LEATHER_BOOTS, "moderation.gui.moderate.kick", ModerationPermissions.KICK, ModerationActionIds.KICK,
                false, ConfirmationDialog.DangerLevel.WARNING),
        BAN(Material.BARRIER, "moderation.gui.moderate.ban", ModerationPermissions.BAN, ModerationActionIds.BAN,
                false, ConfirmationDialog.DangerLevel.DANGEROUS),
        TEMP_BAN(Material.IRON_BARS, "moderation.gui.moderate.tempban", ModerationPermissions.TEMPBAN, ModerationActionIds.TEMP_BAN,
                true, ConfirmationDialog.DangerLevel.DANGEROUS),
        IP_BAN(Material.REDSTONE_BLOCK, "moderation.gui.moderate.ipban", ModerationPermissions.IPBAN, ModerationActionIds.IP_BAN,
                true, ConfirmationDialog.DangerLevel.DANGEROUS),
        MUTE(Material.NOTE_BLOCK, "moderation.gui.moderate.mute", ModerationPermissions.MUTE, ModerationActionIds.MUTE,
                false, ConfirmationDialog.DangerLevel.WARNING),
        TEMP_MUTE(Material.JUKEBOX, "moderation.gui.moderate.tempmute", ModerationPermissions.TEMPMUTE, ModerationActionIds.TEMP_MUTE,
                true, ConfirmationDialog.DangerLevel.WARNING),
        WARN(Material.BOOK, "moderation.gui.moderate.warn", ModerationPermissions.WARN, ModerationActionIds.WARN,
                false, ConfirmationDialog.DangerLevel.NORMAL),
        FREEZE(Material.PACKED_ICE, "moderation.gui.moderate.freeze", ModerationPermissions.FREEZE, ModerationActionIds.FREEZE,
                false, ConfirmationDialog.DangerLevel.WARNING),
        UNBAN(Material.LIME_DYE, "moderation.gui.moderate.unban", ModerationPermissions.UNBAN, ModerationActionIds.UNBAN,
                false, ConfirmationDialog.DangerLevel.NORMAL),
        UNMUTE(Material.LIME_DYE, "moderation.gui.moderate.unmute", ModerationPermissions.UNMUTE, ModerationActionIds.UNMUTE,
                false, ConfirmationDialog.DangerLevel.NORMAL),
        UNFREEZE(Material.LIME_DYE, "moderation.gui.moderate.unfreeze", ModerationPermissions.UNFREEZE, ModerationActionIds.UNFREEZE,
                false, ConfirmationDialog.DangerLevel.NORMAL),
        REMOVE_WARN(Material.GRAY_DYE, "moderation.gui.moderate.removewarn", ModerationPermissions.REMOVE_WARN, ModerationActionIds.REMOVE_WARN,
                false, ConfirmationDialog.DangerLevel.NORMAL);

        private final Material material;
        private final String labelKey;
        private final PermissionNode permission;
        private final ActionId actionId;
        private final boolean needsDuration;
        private final ConfirmationDialog.DangerLevel dangerLevel;

        WizardType(Material material, String labelKey, PermissionNode permission, ActionId actionId,
                boolean needsDuration, ConfirmationDialog.DangerLevel dangerLevel) {
            this.material = material;
            this.labelKey = labelKey;
            this.permission = permission;
            this.actionId = actionId;
            this.needsDuration = needsDuration;
            this.dangerLevel = dangerLevel;
        }
    }
}

package dev.universaladmin.modules.moderation.gui;

import dev.universaladmin.action.ActionId;
import dev.universaladmin.core.time.DurationParseException;
import dev.universaladmin.core.time.DurationParser;
import dev.universaladmin.gui.AbstractGuiPage;
import dev.universaladmin.gui.ConfirmationDialog;
import dev.universaladmin.gui.GuiButton;
import dev.universaladmin.gui.GuiClickContext;
import dev.universaladmin.gui.GuiForm;
import dev.universaladmin.gui.GuiItem;
import dev.universaladmin.gui.GuiLayout;
import dev.universaladmin.gui.GuiPage;
import dev.universaladmin.gui.GuiPageId;
import dev.universaladmin.gui.GuiRenderContext;
import dev.universaladmin.gui.GuiTextInput;
import dev.universaladmin.gui.GuiView;
import dev.universaladmin.gui.SelectionDialog;
import dev.universaladmin.modules.moderation.ModerationFormat;
import dev.universaladmin.modules.moderation.ModerationPermissions;
import dev.universaladmin.modules.moderation.ModerationSettings;
import dev.universaladmin.modules.moderation.action.BanInput;
import dev.universaladmin.modules.moderation.action.FreezeInput;
import dev.universaladmin.modules.moderation.action.IpBanInput;
import dev.universaladmin.modules.moderation.action.KickInput;
import dev.universaladmin.modules.moderation.action.ModerationActionIds;
import dev.universaladmin.modules.moderation.action.MuteInput;
import dev.universaladmin.modules.moderation.action.TemplateInput;
import dev.universaladmin.modules.moderation.action.UnbanInput;
import dev.universaladmin.modules.moderation.action.UnfreezeInput;
import dev.universaladmin.modules.moderation.action.UnmuteInput;
import dev.universaladmin.modules.moderation.action.WarnInput;
import dev.universaladmin.modules.moderation.templates.PunishmentTemplateService;
import dev.universaladmin.modules.moderation.templates.TemplateStep;
import dev.universaladmin.modules.moderation.templates.TemplateTexts;
import dev.universaladmin.permission.PermissionNode;
import dev.universaladmin.settings.CoreSettings;
import dev.universaladmin.status.MuteKind;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * The moderation wizard entry point: Player -&gt; Moderate -&gt; Type -&gt;
 * one form for reason, duration (if needed) and mute kind (with a voice
 * chat connected) -&gt; Confirmation -&gt; Action. Ephemeral,
 * built fresh per click with the target baked in (like {@code
 * PlayerProfilePage}), never registered in {@code GuiRegistry}, and
 * published to the Players module via a {@link ModeratePlayerMenuEntry} instead
 * of a direct import.
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

    private static final String REASON = "reason";
    private static final String OWN_REASON = "own_reason";
    private static final String DURATION = "duration";
    private static final String OWN_DURATION = "own_duration";
    private static final String KIND = "kind";
    /** The {@code punishments.reason} column holds 1024; a form keeps it to what fits in a chat line or two. */
    private static final int MAX_REASON_LENGTH = 255;

    private final ModerationGuiContext ctx;
    private final UUID targetId;
    private final String targetName;
    /** Where a punishment started elsewhere comes back to; {@code null} for this page. */
    private final GuiPage returnTo;

    public ModeratePlayerPage(ModerationGuiContext ctx, UUID targetId, String targetName) {
        this(ctx, targetId, targetName, null);
    }

    private ModeratePlayerPage(ModerationGuiContext ctx, UUID targetId, String targetName, GuiPage returnTo) {
        super(ID, ctx.framework(), ctx.messages());
        this.ctx = ctx;
        this.targetId = targetId;
        this.targetName = targetName;
        this.returnTo = returnTo;
    }

    /**
     * Starts one punishment (or lifts one) for {@code targetId} straight from
     * another page - the moderation row of a player's profile - and comes back
     * to that page afterwards.
     */
    static void start(ModerationGuiContext ctx, GuiClickContext clickCtx, UUID targetId, String targetName, WizardType type) {
        new ModeratePlayerPage(ctx, targetId, targetName, clickCtx.page()).onTypeSelected(clickCtx, type);
    }

    private void back(Player viewer) {
        (returnTo != null ? returnTo : this).open(viewer);
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
        if (!ctx.templates().templates().isEmpty()) {
            buttons.add(new GuiButton(GuiItem.of(Material.ANVIL, text("moderation.gui.moderate.template"),
                    List.of(text("moderation.gui.moderate.template-lore"))), ModerationPermissions.PUNISH,
                    clickCtx -> promptTemplate(clickCtx.viewer())));
        }
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
            case UNBAN -> ModerationGuiActions.runAction(ctx, viewer, ModerationActionIds.UNBAN, new UnbanInput(targetId), () -> back(viewer));
            case UNMUTE -> ModerationGuiActions.runAction(ctx, viewer, ModerationActionIds.UNMUTE, new UnmuteInput(targetId), () -> back(viewer));
            case UNFREEZE -> ModerationGuiActions.runAction(ctx, viewer, ModerationActionIds.UNFREEZE, new UnfreezeInput(targetId), () -> back(viewer));
            // REMOVE_WARN removes one specific warning at a time - route to
            // the target's warnings list, where PunishmentDetailPage's own
            // revoke button (gated on the same permission) does the removal.
            case REMOVE_WARN -> clickCtx.open(PunishmentListPage.warningsForTarget(ctx, targetId));
            default -> promptDetails(viewer, type);
        }
    }

    /** Template -&gt; Confirmation -&gt; {@code moderation.punish}; each template shows the step this player would get. */
    private void promptTemplate(Player viewer) {
        ctx.templates().preview(targetId).whenComplete((choices, error) -> ctx.scheduler().runOnMainThread(() -> {
            if (error != null || choices.isEmpty()) {
                ModerationGuiActions.notifyError(viewer, messages);
                back(viewer);
                return;
            }
            SelectionDialog.open(viewer, framework, messages, ctx.scheduler(), text("moderation.gui.moderate.select-template"), choices,
                    choice -> GuiItem.of(templateMaterial(choice.step()), Component.text(choice.template().name()), List.of(
                            text("moderation.gui.moderate.template-next", TemplateTexts.step(messages, choice.step()),
                                    choice.stepNumber(), choice.template().steps().size()),
                            text("moderation.gui.moderate.confirm-reason", choice.template().reason()))),
                    (clickCtx, choice) -> confirmTemplate(viewer, choice));
        }));
    }

    private void confirmTemplate(Player viewer, PunishmentTemplateService.Choice choice) {
        Runnable apply = () -> ModerationGuiActions.runAction(ctx, viewer, ModerationActionIds.PUNISH,
                new TemplateInput(targetId, choice.template().id(), null), () -> back(viewer));
        if (!ctx.settings().get(CoreSettings.GUI_CONFIRMATIONS)) {
            apply.run();
            return;
        }
        List<Component> description = List.of(
                text("moderation.gui.moderate.confirm-target", targetName),
                text("moderation.gui.moderate.confirm-template", choice.template().name(),
                        TemplateTexts.step(messages, choice.step()), choice.stepNumber(), choice.template().steps().size()),
                text("moderation.gui.moderate.confirm-reason", choice.template().reason()));
        ConfirmationDialog.DangerLevel danger = switch (choice.step().kind()) {
            case BAN -> ConfirmationDialog.DangerLevel.DANGEROUS;
            case MUTE, KICK -> ConfirmationDialog.DangerLevel.WARNING;
            case WARN -> ConfirmationDialog.DangerLevel.NORMAL;
        };
        ConfirmationDialog.open(viewer, framework, messages, text("moderation.gui.moderate.template"), description, danger,
                confirmCtx -> apply.run(), confirmCtx -> back(confirmCtx.viewer()));
    }

    private static Material templateMaterial(TemplateStep step) {
        return switch (step.kind()) {
            case WARN -> Material.BOOK;
            case KICK -> Material.LEATHER_BOOTS;
            case MUTE -> step.permanent() ? Material.NOTE_BLOCK : Material.JUKEBOX;
            case BAN -> step.permanent() ? Material.BARRIER : Material.IRON_BARS;
        };
    }

    /**
     * Reason, duration and - with a voice chat connected - the mute kind in
     * one form (ADR 0023). A typed reason or duration wins over the preset
     * chosen above it.
     */
    private void promptDetails(Player viewer, WizardType type) {
        List<String> reasons = ctx.settings().get(ModerationSettings.REASON_PRESETS);
        List<String> durations = type.needsDuration ? ctx.settings().get(ModerationSettings.DURATION_PRESETS) : List.of();
        GuiForm.Builder form = GuiForm.builder(text("moderation.gui.moderate.title", targetName))
                .message(text(type.labelKey));
        if (!reasons.isEmpty()) {
            form.choice(REASON, text("moderation.gui.moderate.form-reason"), presetOptions(reasons), null);
        }
        form.text(OWN_REASON, text(reasons.isEmpty() ? "moderation.gui.moderate.form-reason" : "moderation.gui.moderate.form-own-reason"),
                "", MAX_REASON_LENGTH);
        if (type.needsDuration) {
            if (!durations.isEmpty()) {
                form.choice(DURATION, text("moderation.gui.moderate.form-duration"), presetOptions(durations), null);
            }
            form.text(OWN_DURATION, text(durations.isEmpty()
                    ? "moderation.gui.moderate.prompt-duration" : "moderation.gui.moderate.form-own-duration"), "", 32);
        }
        if (type.mutes() && ctx.voiceChat().isPresent()) {
            form.choice(KIND, text("moderation.gui.moderate.form-kind"), List.of(
                    new GuiForm.Option(MuteKind.FULL.name(), text("moderation.gui.moderate.kind-full")),
                    new GuiForm.Option(MuteKind.CHAT.name(), text("moderation.gui.moderate.kind-chat")),
                    new GuiForm.Option(MuteKind.VOICE.name(), text("moderation.gui.moderate.kind-voice"))), MuteKind.FULL.name());
        }
        form.open(viewer, text("moderation.gui.moderate.form-continue"), text("gui.cancel"),
                response -> onDetails(viewer, type, reasons, durations, response),
                () -> back(viewer));
    }

    private void onDetails(Player viewer, WizardType type, List<String> reasons, List<String> durations, GuiForm.Response response) {
        String reason = Optional.ofNullable(response.textOrNull(OWN_REASON)).orElseGet(() -> preset(reasons, response.choice(REASON)));
        if (reason == null) {
            ModerationGuiActions.notifyError(viewer, messages);
            back(viewer);
            return;
        }
        Instant expiresAt = null;
        if (type.needsDuration) {
            String duration = Optional.ofNullable(response.textOrNull(OWN_DURATION)).orElseGet(() -> preset(durations, response.choice(DURATION)));
            try {
                expiresAt = duration == null ? null : DurationParser.parse(duration).map(Instant.now()::plus).orElse(null);
            } catch (DurationParseException e) {
                ModerationGuiActions.notifyError(viewer, messages);
                back(viewer);
                return;
            }
        }
        MuteKind kind = MuteKind.FULL;
        if (type.mutes() && !response.choice(KIND).isEmpty()) {
            kind = MuteKind.valueOf(response.choice(KIND));
        }
        promptConfirm(viewer, type, reason, expiresAt, kind);
    }

    /** One option per preset, identified by its position. */
    private static List<GuiForm.Option> presetOptions(List<String> presets) {
        List<GuiForm.Option> options = new ArrayList<>();
        for (int i = 0; i < presets.size(); i++) {
            options.add(new GuiForm.Option(Integer.toString(i), Component.text(presets.get(i))));
        }
        return options;
    }

    /** The preset a choice picked, {@code null} if none. */
    private static String preset(List<String> presets, String choice) {
        try {
            int index = Integer.parseInt(choice);
            return index >= 0 && index < presets.size() ? presets.get(index) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void promptConfirm(Player viewer, WizardType type, String reason, Instant expiresAt, MuteKind kind) {
        if (!ctx.settings().get(CoreSettings.GUI_CONFIRMATIONS)) {
            apply(viewer, type, reason, expiresAt, kind);
            return;
        }
        List<Component> description = new ArrayList<>();
        description.add(text("moderation.gui.moderate.confirm-target", targetName));
        description.add(text("moderation.gui.moderate.confirm-reason", reason));
        if (type.needsDuration) {
            description.add(text("moderation.gui.moderate.confirm-expires", ModerationFormat.expiry(expiresAt, ctx.settings(), messages)));
        }
        if (type.mutes() && kind != MuteKind.FULL) {
            description.add(text("moderation.gui.moderate.confirm-kind", text(kind == MuteKind.CHAT
                    ? "moderation.gui.moderate.kind-chat" : "moderation.gui.moderate.kind-voice")));
        }
        ConfirmationDialog.open(viewer, framework, messages, text(type.labelKey), description, type.dangerLevel,
                confirmCtx -> apply(confirmCtx.viewer(), type, reason, expiresAt, kind),
                confirmCtx -> back(confirmCtx.viewer()));
    }

    private void apply(Player viewer, WizardType type, String reason, Instant expiresAt, MuteKind kind) {
        ActionId actionId = type.actionId;
        Object input = switch (type) {
            case KICK -> new KickInput(targetId, reason);
            case BAN -> new BanInput(targetId, reason, null);
            case TEMP_BAN -> new BanInput(targetId, reason, expiresAt);
            case IP_BAN -> new IpBanInput(targetId, reason, expiresAt);
            case MUTE -> new MuteInput(targetId, reason, null, null, kind);
            case TEMP_MUTE -> new MuteInput(targetId, reason, expiresAt, null, kind);
            case WARN -> new WarnInput(targetId, reason);
            case FREEZE -> new FreezeInput(targetId, reason);
            case UNBAN, UNMUTE, UNFREEZE, REMOVE_WARN -> throw new IllegalStateException("Unreachable: " + type);
        };
        ModerationGuiActions.runAction(ctx, viewer, actionId, input, () -> back(viewer));
    }

    /** One entry per wizard type; REMOVE_WARN is handled entirely by {@link #onTypeSelected} routing to a picker instead. */
    enum WizardType {
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

        boolean mutes() {
            return this == MUTE || this == TEMP_MUTE;
        }

        Material material() {
            return material;
        }

        String labelKey() {
            return labelKey;
        }

        PermissionNode permission() {
            return permission;
        }
    }
}

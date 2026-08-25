package dev.universaladmin.modules.server.gui;

import dev.universaladmin.gui.AbstractGuiPage;
import dev.universaladmin.gui.ConfirmationDialog;
import dev.universaladmin.gui.GuiButton;
import dev.universaladmin.gui.GuiItem;
import dev.universaladmin.gui.GuiLayout;
import dev.universaladmin.gui.GuiPageId;
import dev.universaladmin.gui.GuiRenderContext;
import dev.universaladmin.gui.GuiTextInput;
import dev.universaladmin.gui.GuiView;
import dev.universaladmin.gui.SelectionDialog;
import dev.universaladmin.modules.server.MaintenanceState;
import dev.universaladmin.modules.server.action.EnableMaintenanceInput;
import dev.universaladmin.modules.server.action.ServerActionIds;
import dev.universaladmin.settings.CoreSettings;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * Maintenance mode's own management page: status, reason, kick-on-enable
 * choice, and the allow-list - reached from {@link ServerHomePage}'s
 * "Maintenance" button, gated on {@link dev.universaladmin.modules.server.ServerPermissions#MAINTENANCE}.
 */
public final class ServerMaintenancePage extends AbstractGuiPage {

    public static final GuiPageId ID = GuiPageId.core("server.maintenance");

    private final ServerGuiContext ctx;

    public ServerMaintenancePage(ServerGuiContext ctx) {
        super(ID, ctx.framework(), ctx.messages());
        this.ctx = ctx;
    }

    @Override
    protected Component title(Player viewer) {
        return text("server.gui.maintenance.title");
    }

    @Override
    protected void renderContent(GuiRenderContext context) {
        GuiView view = context.view();
        Player viewer = context.viewer();
        MaintenanceState state = ctx.maintenanceService().current();

        List<Component> statusLore = state.enabled()
                ? List.of(text("server.gui.maintenance.status-reason", state.reason() == null ? "" : state.reason()))
                : List.of();
        view.place(GuiLayout.CONTENT_START_SLOT, GuiItem.of(state.enabled() ? Material.RED_WOOL : Material.LIME_WOOL,
                text(state.enabled() ? "server.gui.maintenance.status-on" : "server.gui.maintenance.status-off"), statusLore));

        view.place(GuiLayout.CONTENT_START_SLOT + 1,
                new GuiButton(GuiItem.of(state.enabled() ? Material.LIME_DYE : Material.RED_DYE,
                        text(state.enabled() ? "server.gui.maintenance.disable" : "server.gui.maintenance.enable")),
                        null, clickCtx -> onToggle(clickCtx.viewer(), state.enabled())), viewer);

        view.place(GuiLayout.CONTENT_START_SLOT + 2, new GuiButton(GuiItem.of(Material.NAME_TAG,
                        text("server.gui.maintenance.allowed-players"),
                        List.of(text("server.gui.maintenance.allowed-players-lore", state.allowedPlayers().size()))),
                        null, clickCtx -> promptAllowedPlayers(clickCtx.viewer(), state)), viewer);
    }

    private void onToggle(Player viewer, boolean currentlyEnabled) {
        if (currentlyEnabled) {
            confirmDisable(viewer);
        } else {
            promptEnableReason(viewer);
        }
    }

    private void confirmDisable(Player viewer) {
        Runnable apply = () -> ServerGuiActions.<Void>runAction(ctx, viewer, ServerActionIds.MAINTENANCE_DISABLE, null, () -> this.open(viewer));
        if (!ctx.settings().get(CoreSettings.GUI_CONFIRMATIONS)) {
            apply.run();
            return;
        }
        ConfirmationDialog.open(viewer, framework, messages, text("server.gui.maintenance.disable"),
                List.of(text("server.gui.maintenance.confirm-disable")), ConfirmationDialog.DangerLevel.WARNING,
                confirmCtx -> apply.run(), confirmCtx -> confirmCtx.back());
    }

    private void promptEnableReason(Player viewer) {
        GuiTextInput.request(viewer, text("server.gui.maintenance.enable"), text("server.gui.prompt.reason-optional"), "",
                text("gui.confirm"), text("gui.cancel"),
                reason -> promptKickChoice(viewer, reason == null || reason.isBlank() ? null : reason.trim()),
                () -> this.open(viewer));
    }

    private void promptKickChoice(Player viewer, String reason) {
        SelectionDialog.open(viewer, framework, messages, ctx.scheduler(),
                text("server.gui.maintenance.kick-prompt"), List.of(Boolean.TRUE, Boolean.FALSE),
                kick -> GuiItem.of(kick ? Material.MAGMA_BLOCK : Material.PAPER,
                        text(kick ? "server.gui.maintenance.kick-yes" : "server.gui.maintenance.kick-no")),
                (selectCtx, kick) -> confirmEnable(viewer, reason, kick));
    }

    private void confirmEnable(Player viewer, String reason, boolean kickNonBypass) {
        Runnable apply = () -> ServerGuiActions.runAction(ctx, viewer, ServerActionIds.MAINTENANCE_ENABLE,
                new EnableMaintenanceInput(reason, null, kickNonBypass), () -> this.open(viewer));
        if (!ctx.settings().get(CoreSettings.GUI_CONFIRMATIONS)) {
            apply.run();
            return;
        }
        ConfirmationDialog.open(viewer, framework, messages, text("server.gui.maintenance.enable"),
                List.of(text("server.gui.maintenance.confirm-enable")), ConfirmationDialog.DangerLevel.DANGEROUS,
                confirmCtx -> apply.run(), confirmCtx -> confirmCtx.back());
    }

    private void promptAllowedPlayers(Player viewer, MaintenanceState state) {
        String initial = String.join(", ", state.allowedPlayers());
        GuiTextInput.request(viewer, text("server.gui.maintenance.allowed-players"), text("server.gui.prompt.allowed-players"), initial,
                text("gui.confirm"), text("gui.cancel"),
                submitted -> {
                    Set<String> names = submitted == null || submitted.isBlank()
                            ? Set.of()
                            : Arrays.stream(submitted.split(",")).map(String::trim).filter(name -> !name.isEmpty()).collect(Collectors.toSet());
                    ServerGuiActions.runAction(ctx, viewer, ServerActionIds.MAINTENANCE_SET_ALLOWED_PLAYERS, names, () -> this.open(viewer));
                },
                () -> this.open(viewer));
    }
}

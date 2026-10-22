package dev.universaladmin.modules.server.gui;

import dev.universaladmin.action.ActionId;
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
import dev.universaladmin.modules.server.MaintenanceScope;
import dev.universaladmin.modules.server.MaintenanceState;
import dev.universaladmin.modules.server.ServerPermissions;
import dev.universaladmin.modules.server.action.EnableMaintenanceInput;
import dev.universaladmin.modules.server.action.ServerActionIds;
import dev.universaladmin.permission.PermissionNode;
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
 * "Maintenance" button, gated on {@link ServerPermissions#MAINTENANCE}. The
 * first row is this server's maintenance; in network mode a second row is
 * the whole network's ({@link ServerPermissions#MAINTENANCE_NETWORK}).
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
        renderRow(context.view(), context.viewer(), MaintenanceScope.SERVER, GuiLayout.CONTENT_START_SLOT);
        if (ctx.maintenanceService().networkAvailable()) {
            renderRow(context.view(), context.viewer(), MaintenanceScope.NETWORK, GuiLayout.CONTENT_START_SLOT + GuiLayout.COLUMNS);
        }
    }

    private void renderRow(GuiView view, Player viewer, MaintenanceScope scope, int slot) {
        MaintenanceState state = ctx.maintenanceService().current(scope);
        PermissionNode permission = scope == MaintenanceScope.NETWORK ? ServerPermissions.MAINTENANCE_NETWORK : null;

        List<Component> statusLore = state.enabled()
                ? List.of(text("server.gui.maintenance.status-reason", state.reason() == null ? "" : state.reason()))
                : List.of();
        view.place(slot, GuiItem.of(state.enabled() ? Material.RED_WOOL : Material.LIME_WOOL,
                text(key(scope, state.enabled() ? "status-on" : "status-off")), statusLore));

        view.place(slot + 1,
                new GuiButton(GuiItem.of(state.enabled() ? Material.LIME_DYE : Material.RED_DYE,
                        text(key(scope, state.enabled() ? "disable" : "enable"))),
                        permission, clickCtx -> onToggle(clickCtx.viewer(), scope, state.enabled())), viewer);

        view.place(slot + 2, new GuiButton(GuiItem.of(Material.NAME_TAG,
                        text(key(scope, "allowed-players")),
                        List.of(text("server.gui.maintenance.allowed-players-lore", state.allowedPlayers().size()))),
                        permission, clickCtx -> promptAllowedPlayers(clickCtx.viewer(), scope, state)), viewer);
    }

    /** {@code server.gui.maintenance.<name>}, or {@code network-<name>} for the network's row. */
    private static String key(MaintenanceScope scope, String name) {
        return "server.gui.maintenance." + (scope == MaintenanceScope.NETWORK ? "network-" : "") + name;
    }

    private void onToggle(Player viewer, MaintenanceScope scope, boolean currentlyEnabled) {
        if (currentlyEnabled) {
            confirmDisable(viewer, scope);
        } else {
            promptEnableReason(viewer, scope);
        }
    }

    private void confirmDisable(Player viewer, MaintenanceScope scope) {
        ActionId action = scope == MaintenanceScope.NETWORK ? ServerActionIds.NETWORK_MAINTENANCE_DISABLE : ServerActionIds.MAINTENANCE_DISABLE;
        Runnable apply = () -> ServerGuiActions.<Void>runAction(ctx, viewer, action, null, () -> this.open(viewer));
        if (!ctx.settings().get(CoreSettings.GUI_CONFIRMATIONS)) {
            apply.run();
            return;
        }
        ConfirmationDialog.open(viewer, framework, messages, text(key(scope, "disable")),
                List.of(text(key(scope, "confirm-disable"))), ConfirmationDialog.DangerLevel.WARNING,
                confirmCtx -> apply.run(), confirmCtx -> confirmCtx.back());
    }

    private void promptEnableReason(Player viewer, MaintenanceScope scope) {
        GuiTextInput.request(viewer, text(key(scope, "enable")), text("server.gui.prompt.reason-optional"), "",
                text("gui.confirm"), text("gui.cancel"),
                reason -> promptKickChoice(viewer, scope, reason == null || reason.isBlank() ? null : reason.trim()),
                () -> this.open(viewer));
    }

    private void promptKickChoice(Player viewer, MaintenanceScope scope, String reason) {
        SelectionDialog.open(viewer, framework, messages, ctx.scheduler(),
                text("server.gui.maintenance.kick-prompt"), List.of(Boolean.TRUE, Boolean.FALSE),
                kick -> GuiItem.of(kick ? Material.MAGMA_BLOCK : Material.PAPER,
                        text(kick ? "server.gui.maintenance.kick-yes" : "server.gui.maintenance.kick-no")),
                (selectCtx, kick) -> confirmEnable(viewer, scope, reason, kick));
    }

    private void confirmEnable(Player viewer, MaintenanceScope scope, String reason, boolean kickNonBypass) {
        ActionId action = scope == MaintenanceScope.NETWORK ? ServerActionIds.NETWORK_MAINTENANCE_ENABLE : ServerActionIds.MAINTENANCE_ENABLE;
        Runnable apply = () -> ServerGuiActions.runAction(ctx, viewer, action,
                new EnableMaintenanceInput(reason, null, kickNonBypass), () -> this.open(viewer));
        if (!ctx.settings().get(CoreSettings.GUI_CONFIRMATIONS)) {
            apply.run();
            return;
        }
        ConfirmationDialog.open(viewer, framework, messages, text(key(scope, "enable")),
                List.of(text(key(scope, "confirm-enable"))), ConfirmationDialog.DangerLevel.DANGEROUS,
                confirmCtx -> apply.run(), confirmCtx -> confirmCtx.back());
    }

    private void promptAllowedPlayers(Player viewer, MaintenanceScope scope, MaintenanceState state) {
        ActionId action = scope == MaintenanceScope.NETWORK
                ? ServerActionIds.NETWORK_MAINTENANCE_SET_ALLOWED_PLAYERS : ServerActionIds.MAINTENANCE_SET_ALLOWED_PLAYERS;
        String initial = String.join(", ", state.allowedPlayers());
        GuiTextInput.request(viewer, text(key(scope, "allowed-players")), text("server.gui.prompt.allowed-players"), initial,
                text("gui.confirm"), text("gui.cancel"),
                submitted -> {
                    Set<String> names = submitted == null || submitted.isBlank()
                            ? Set.of()
                            : Arrays.stream(submitted.split(",")).map(String::trim).filter(name -> !name.isEmpty()).collect(Collectors.toSet());
                    ServerGuiActions.runAction(ctx, viewer, action, names, () -> this.open(viewer));
                },
                () -> this.open(viewer));
    }
}

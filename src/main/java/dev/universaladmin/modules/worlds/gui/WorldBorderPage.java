package dev.universaladmin.modules.worlds.gui;

import dev.universaladmin.gui.AbstractGuiPage;
import dev.universaladmin.gui.GuiButton;
import dev.universaladmin.gui.GuiForm;
import dev.universaladmin.gui.GuiItem;
import dev.universaladmin.gui.GuiLayout;
import dev.universaladmin.gui.GuiPageId;
import dev.universaladmin.gui.GuiRenderContext;
import dev.universaladmin.gui.GuiView;
import dev.universaladmin.modules.worlds.WorldBorderSnapshot;
import dev.universaladmin.modules.worlds.WorldsPermissions;
import dev.universaladmin.modules.worlds.action.SetWorldBorderCenterInput;
import dev.universaladmin.modules.worlds.action.SetWorldBorderDamageInput;
import dev.universaladmin.modules.worlds.action.SetWorldBorderSizeInput;
import dev.universaladmin.modules.worlds.action.SetWorldBorderWarningInput;
import dev.universaladmin.modules.worlds.action.WorldActionIds;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * Ephemeral World Border management page - center/size/damage/warning,
 * covering everything {@link org.bukkit.WorldBorder} exposes ("soweit API").
 */
public final class WorldBorderPage extends AbstractGuiPage {

    public static final GuiPageId ID = GuiPageId.core("worlds.border");

    /** The slider's end - longer transitions are a command away ({@code /admin world border}). */
    private static final float MAX_TRANSITION_SECONDS = 600;

    private final WorldsGuiContext ctx;
    private final String worldName;

    public WorldBorderPage(WorldsGuiContext ctx, String worldName) {
        super(ID, ctx.framework(), ctx.messages());
        this.ctx = ctx;
        this.worldName = worldName;
    }

    @Override
    protected Component title(Player viewer) {
        return text("worlds.gui.border.title", worldName);
    }

    @Override
    protected void renderContent(GuiRenderContext context) {
        GuiView view = context.view();
        Player viewer = context.viewer();
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            view.place(GuiLayout.contentSlot(GuiLayout.contentSize() / 2), GuiItem.of(framework.icons().empty(), text("worlds.gui.not-found")));
            return;
        }
        WorldBorderSnapshot border = ctx.infoService().border(world);

        int slot = GuiLayout.CONTENT_START_SLOT;
        view.place(slot++, field("worlds.gui.border.center", WorldsGuiFormat.coordinates(border.center())));
        view.place(slot++, field("worlds.gui.border.size", "%.0f".formatted(border.size())));
        view.place(slot++, field("worlds.gui.border.damage-amount", String.valueOf(border.damageAmount())));
        view.place(slot++, field("worlds.gui.border.damage-buffer", String.valueOf(border.damageBuffer())));
        view.place(slot++, field("worlds.gui.border.warning-distance", String.valueOf(border.warningDistance())));
        view.place(slot++, field("worlds.gui.border.warning-time", String.valueOf(border.warningTime())));

        int actionSlot = GuiLayout.CONTENT_START_SLOT + GuiLayout.COLUMNS;
        view.place(actionSlot++, new GuiButton(GuiItem.of(Material.COMPASS, text("worlds.gui.border.set-center")),
                WorldsPermissions.BORDER, clickCtx -> promptCenter(clickCtx.viewer(), border)), viewer);
        view.place(actionSlot++, new GuiButton(GuiItem.of(Material.MAP, text("worlds.gui.border.set-size")),
                WorldsPermissions.BORDER, clickCtx -> promptSize(clickCtx.viewer(), border)), viewer);
        view.place(actionSlot++, new GuiButton(GuiItem.of(Material.MAGMA_BLOCK, text("worlds.gui.border.set-damage")),
                WorldsPermissions.BORDER, clickCtx -> promptDamage(clickCtx.viewer(), border)), viewer);
        view.place(actionSlot, new GuiButton(GuiItem.of(Material.BELL, text("worlds.gui.border.set-warning")),
                WorldsPermissions.BORDER, clickCtx -> promptWarning(clickCtx.viewer(), border)), viewer);
    }

    /** Center as two fields, or the viewer's own position (ADR 0023). */
    private void promptCenter(Player viewer, WorldBorderSnapshot border) {
        GuiForm.builder(text("worlds.gui.border.set-center"))
                .text("x", text("worlds.gui.prompt.border-x"), "%.0f".formatted(border.center().getX()), 16)
                .text("z", text("worlds.gui.prompt.border-z"), "%.0f".formatted(border.center().getZ()), 16)
                .toggle("here", text("worlds.gui.prompt.border-here"), false)
                .open(viewer, text("gui.confirm"), text("gui.cancel"), response -> {
                    boolean here = response.toggle("here") && viewer.getWorld().getName().equals(worldName);
                    Double x = here ? Double.valueOf(viewer.getLocation().getX()) : parseDouble(response.text("x"));
                    Double z = here ? Double.valueOf(viewer.getLocation().getZ()) : parseDouble(response.text("z"));
                    if (x == null || z == null) {
                        WorldsGuiActions.notifyError(viewer, messages);
                        this.open(viewer);
                        return;
                    }
                    WorldsGuiActions.runAction(ctx, viewer, WorldActionIds.SET_BORDER_CENTER,
                            new SetWorldBorderCenterInput(worldName, x, z), () -> this.open(viewer));
                }, () -> this.open(viewer));
    }

    /** Size as a field (it reaches millions of blocks), the transition time as a slider. */
    private void promptSize(Player viewer, WorldBorderSnapshot border) {
        GuiForm.builder(text("worlds.gui.border.set-size"))
                .text("size", text("worlds.gui.prompt.border-size-value"), "%.0f".formatted(border.size()), 16)
                .number("transition", text("worlds.gui.prompt.border-transition"), 0, MAX_TRANSITION_SECONDS, 5, 0)
                .open(viewer, text("gui.confirm"), text("gui.cancel"), response -> {
                    Double size = parseDouble(response.text("size"));
                    long transition = Math.round(response.number("transition"));
                    if (size == null) {
                        WorldsGuiActions.notifyError(viewer, messages);
                        this.open(viewer);
                        return;
                    }
                    WorldsGuiActions.runAction(ctx, viewer, WorldActionIds.SET_BORDER_SIZE,
                            new SetWorldBorderSizeInput(worldName, size, transition == 0 ? null : transition), () -> this.open(viewer));
                }, () -> this.open(viewer));
    }

    private void promptDamage(Player viewer, WorldBorderSnapshot border) {
        GuiForm.builder(text("worlds.gui.border.set-damage"))
                .text("amount", text("worlds.gui.prompt.border-damage-amount"), String.valueOf(border.damageAmount()), 16)
                .text("buffer", text("worlds.gui.prompt.border-damage-buffer"), String.valueOf(border.damageBuffer()), 16)
                .open(viewer, text("gui.confirm"), text("gui.cancel"), response -> {
                    Double amount = parseDouble(response.text("amount"));
                    Double buffer = parseDouble(response.text("buffer"));
                    if (amount == null || buffer == null) {
                        WorldsGuiActions.notifyError(viewer, messages);
                        this.open(viewer);
                        return;
                    }
                    WorldsGuiActions.runAction(ctx, viewer, WorldActionIds.SET_BORDER_DAMAGE,
                            new SetWorldBorderDamageInput(worldName, amount, buffer), () -> this.open(viewer));
                }, () -> this.open(viewer));
    }

    private void promptWarning(Player viewer, WorldBorderSnapshot border) {
        GuiForm.builder(text("worlds.gui.border.set-warning"))
                .text("distance", text("worlds.gui.prompt.border-warning-distance"), String.valueOf(border.warningDistance()), 10)
                .text("time", text("worlds.gui.prompt.border-warning-time"), String.valueOf(border.warningTime()), 10)
                .open(viewer, text("gui.confirm"), text("gui.cancel"), response -> {
                    Integer distance = parseInt(response.text("distance"));
                    Integer time = parseInt(response.text("time"));
                    if (distance == null || time == null) {
                        WorldsGuiActions.notifyError(viewer, messages);
                        this.open(viewer);
                        return;
                    }
                    WorldsGuiActions.runAction(ctx, viewer, WorldActionIds.SET_BORDER_WARNING,
                            new SetWorldBorderWarningInput(worldName, distance, time), () -> this.open(viewer));
                }, () -> this.open(viewer));
    }

    private Double parseDouble(String value) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private GuiItem field(String labelKey, String value) {
        return GuiItem.of(Material.PAPER, text(labelKey), List.of(Component.text(value, NamedTextColor.GRAY)));
    }
}

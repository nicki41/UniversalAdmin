package dev.universaladmin.modules.worlds.gui;

import dev.universaladmin.gui.AbstractGuiPage;
import dev.universaladmin.gui.GuiButton;
import dev.universaladmin.gui.GuiItem;
import dev.universaladmin.gui.GuiLayout;
import dev.universaladmin.gui.GuiPageId;
import dev.universaladmin.gui.GuiRenderContext;
import dev.universaladmin.gui.GuiTextInput;
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

    private void promptCenter(Player viewer, WorldBorderSnapshot border) {
        String initial = "%.0f %.0f".formatted(border.center().getX(), border.center().getZ());
        GuiTextInput.request(viewer, text("worlds.gui.border.set-center"), text("worlds.gui.prompt.border-center"), initial,
                text("gui.confirm"), text("gui.cancel"),
                submitted -> {
                    double[] xz = parseDoubles(submitted, 2);
                    if (xz == null) {
                        WorldsGuiActions.notifyError(viewer, messages);
                        this.open(viewer);
                        return;
                    }
                    WorldsGuiActions.runAction(ctx, viewer, WorldActionIds.SET_BORDER_CENTER,
                            new SetWorldBorderCenterInput(worldName, xz[0], xz[1]), () -> this.open(viewer));
                },
                () -> this.open(viewer));
    }

    private void promptSize(Player viewer, WorldBorderSnapshot border) {
        GuiTextInput.request(viewer, text("worlds.gui.border.set-size"), text("worlds.gui.prompt.border-size"),
                "%.0f".formatted(border.size()), text("gui.confirm"), text("gui.cancel"),
                submitted -> {
                    String[] parts = submitted == null ? new String[0] : submitted.trim().split("\\s+");
                    Double size = parts.length >= 1 ? parseDouble(parts[0]) : null;
                    Long transition = parts.length >= 2 ? parseLong(parts[1]) : null;
                    if (size == null) {
                        WorldsGuiActions.notifyError(viewer, messages);
                        this.open(viewer);
                        return;
                    }
                    WorldsGuiActions.runAction(ctx, viewer, WorldActionIds.SET_BORDER_SIZE,
                            new SetWorldBorderSizeInput(worldName, size, transition), () -> this.open(viewer));
                },
                () -> this.open(viewer));
    }

    private void promptDamage(Player viewer, WorldBorderSnapshot border) {
        String initial = "%s %s".formatted(border.damageAmount(), border.damageBuffer());
        GuiTextInput.request(viewer, text("worlds.gui.border.set-damage"), text("worlds.gui.prompt.border-damage"), initial,
                text("gui.confirm"), text("gui.cancel"),
                submitted -> {
                    double[] values = parseDoubles(submitted, 2);
                    if (values == null) {
                        WorldsGuiActions.notifyError(viewer, messages);
                        this.open(viewer);
                        return;
                    }
                    WorldsGuiActions.runAction(ctx, viewer, WorldActionIds.SET_BORDER_DAMAGE,
                            new SetWorldBorderDamageInput(worldName, values[0], values[1]), () -> this.open(viewer));
                },
                () -> this.open(viewer));
    }

    private void promptWarning(Player viewer, WorldBorderSnapshot border) {
        String initial = "%d %d".formatted(border.warningDistance(), border.warningTime());
        GuiTextInput.request(viewer, text("worlds.gui.border.set-warning"), text("worlds.gui.prompt.border-warning"), initial,
                text("gui.confirm"), text("gui.cancel"),
                submitted -> {
                    String[] parts = submitted == null ? new String[0] : submitted.trim().split("\\s+");
                    Integer distance = parts.length >= 1 ? parseInt(parts[0]) : null;
                    Integer time = parts.length >= 2 ? parseInt(parts[1]) : null;
                    if (distance == null || time == null) {
                        WorldsGuiActions.notifyError(viewer, messages);
                        this.open(viewer);
                        return;
                    }
                    WorldsGuiActions.runAction(ctx, viewer, WorldActionIds.SET_BORDER_WARNING,
                            new SetWorldBorderWarningInput(worldName, distance, time), () -> this.open(viewer));
                },
                () -> this.open(viewer));
    }

    private double[] parseDoubles(String input, int count) {
        if (input == null) {
            return null;
        }
        String[] parts = input.trim().split("\\s+");
        if (parts.length < count) {
            return null;
        }
        double[] values = new double[count];
        try {
            for (int i = 0; i < count; i++) {
                values[i] = Double.parseDouble(parts[i]);
            }
            return values;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Double parseDouble(String value) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Long parseLong(String value) {
        try {
            return Long.parseLong(value);
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

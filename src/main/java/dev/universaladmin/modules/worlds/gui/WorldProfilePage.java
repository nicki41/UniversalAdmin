package dev.universaladmin.modules.worlds.gui;

import dev.universaladmin.gui.AbstractGuiPage;
import dev.universaladmin.gui.GuiButton;
import dev.universaladmin.gui.GuiItem;
import dev.universaladmin.gui.GuiLayout;
import dev.universaladmin.gui.GuiPageId;
import dev.universaladmin.gui.GuiRenderContext;
import dev.universaladmin.gui.GuiTextInput;
import dev.universaladmin.gui.GuiView;
import dev.universaladmin.gui.SelectionDialog;
import dev.universaladmin.modules.worlds.WeatherState;
import dev.universaladmin.modules.worlds.WorldProfileSnapshot;
import dev.universaladmin.modules.worlds.WorldsPermissions;
import dev.universaladmin.modules.worlds.action.SetWorldDifficultyInput;
import dev.universaladmin.modules.worlds.action.SetWorldSpawnInput;
import dev.universaladmin.modules.worlds.action.SetWorldTimeInput;
import dev.universaladmin.modules.worlds.action.SetWorldWeatherInput;
import dev.universaladmin.modules.worlds.action.WorldActionIds;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * Ephemeral World Profile page - built fresh per click with the target
 * world's name baked in (like {@code PlayerProfilePage}/{@code
 * ModeratePlayerPage}), not registered in {@code GuiRegistry}. Re-resolves
 * the live {@link World} by name on every render rather than holding a
 * {@link World} reference, since a world can be unloaded/reloaded between
 * clicks.
 */
public final class WorldProfilePage extends AbstractGuiPage {

    public static final GuiPageId ID = GuiPageId.core("worlds.profile");

    /** How many trailing content slots the action-button row reserves - fields never spill into it. */
    private static final int ACTION_SLOTS = 7;

    private final WorldsGuiContext ctx;
    private final String worldName;

    public WorldProfilePage(WorldsGuiContext ctx, String worldName) {
        super(ID, ctx.framework(), ctx.messages());
        this.ctx = ctx;
        this.worldName = worldName;
    }

    @Override
    protected Component title(Player viewer) {
        return Component.text(worldName);
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

        boolean includeSeed = viewer.hasPermission(WorldsPermissions.VIEW_SEED.value());
        WorldProfileSnapshot snapshot = ctx.infoService().profile(world, includeSeed);
        renderFields(view, snapshot);
        renderActions(view, viewer, snapshot);
    }

    private void renderFields(GuiView view, WorldProfileSnapshot snapshot) {
        List<GuiItem> fields = new ArrayList<>();
        fields.add(header(snapshot));
        fields.add(field("worlds.gui.field.environment", snapshot.environment().name()));
        if (snapshot.seed() != null) {
            fields.add(field("worlds.gui.field.seed", String.valueOf(snapshot.seed())));
        }
        fields.add(field("worlds.gui.field.spawn", WorldsGuiFormat.coordinates(snapshot.spawn())));
        fields.add(field("worlds.gui.field.players", String.valueOf(snapshot.players())));
        fields.add(field("worlds.gui.field.chunks", String.valueOf(snapshot.loadedChunks())));
        fields.add(field("worlds.gui.field.entities", String.valueOf(snapshot.entities())));
        fields.add(field("worlds.gui.field.difficulty", snapshot.difficulty().name()));
        fields.add(field("worlds.gui.field.time", String.valueOf(snapshot.time())));
        fields.add(field("worlds.gui.field.weather", WorldsGuiFormat.weatherLabel(messages, snapshot.storm(), snapshot.thundering())));
        fields.add(field("worlds.gui.field.border-size", "%.0f".formatted(snapshot.border().size())));

        int maxFieldSlots = GuiLayout.contentSize() - ACTION_SLOTS;
        for (int i = 0; i < fields.size() && i < maxFieldSlots; i++) {
            view.place(GuiLayout.contentSlot(i), fields.get(i));
        }
    }

    private void renderActions(GuiView view, Player viewer, WorldProfileSnapshot snapshot) {
        int slot = GuiLayout.contentSlot(GuiLayout.contentSize() - ACTION_SLOTS);
        view.place(slot++, new GuiButton(GuiItem.of(Material.ENDER_PEARL, text("worlds.gui.buttons.teleport")),
                WorldsPermissions.TELEPORT, clickCtx -> teleport(clickCtx.viewer())), viewer);
        view.place(slot++, new GuiButton(GuiItem.of(Material.RED_BED, text("worlds.gui.buttons.set-spawn")),
                WorldsPermissions.SPAWN, clickCtx -> setSpawn(clickCtx.viewer())), viewer);
        view.place(slot++, new GuiButton(GuiItem.of(Material.CLOCK, text("worlds.gui.buttons.set-time")),
                WorldsPermissions.TIME, clickCtx -> promptTime(clickCtx.viewer())), viewer);
        view.place(slot++, new GuiButton(GuiItem.of(Material.WATER_BUCKET, text("worlds.gui.buttons.set-weather")),
                WorldsPermissions.WEATHER, clickCtx -> promptWeather(clickCtx.viewer())), viewer);
        view.place(slot++, new GuiButton(GuiItem.of(Material.IRON_SWORD, text("worlds.gui.buttons.set-difficulty")),
                WorldsPermissions.DIFFICULTY, clickCtx -> promptDifficulty(clickCtx.viewer())), viewer);
        view.place(slot++, new GuiButton(GuiItem.of(Material.BARRIER, text("worlds.gui.buttons.border")),
                WorldsPermissions.BORDER, clickCtx -> clickCtx.open(new WorldBorderPage(ctx, worldName))), viewer);
        view.place(slot, new GuiButton(GuiItem.of(Material.COMMAND_BLOCK, text("worlds.gui.buttons.gamerules")),
                WorldsPermissions.GAMERULE, clickCtx -> clickCtx.open(new WorldGameRulesListPage(ctx, worldName))), viewer);
    }

    private void teleport(Player viewer) {
        WorldsGuiActions.runAction(ctx, viewer, WorldActionIds.TELEPORT_TO_SPAWN, worldName, () -> this.open(viewer));
    }

    private void setSpawn(Player viewer) {
        WorldsGuiActions.runAction(ctx, viewer, WorldActionIds.SET_SPAWN, SetWorldSpawnInput.atActorLocation(worldName), () -> this.open(viewer));
    }

    private void promptTime(Player viewer) {
        GuiTextInput.request(viewer, text("worlds.gui.buttons.set-time"), text("worlds.gui.prompt.time"), "",
                text("gui.confirm"), text("gui.cancel"),
                submitted -> {
                    Long ticks = parseLong(submitted);
                    if (ticks == null) {
                        WorldsGuiActions.notifyError(viewer, messages);
                        this.open(viewer);
                        return;
                    }
                    WorldsGuiActions.runAction(ctx, viewer, WorldActionIds.SET_TIME, new SetWorldTimeInput(worldName, ticks), () -> this.open(viewer));
                },
                () -> this.open(viewer));
    }

    private void promptWeather(Player viewer) {
        SelectionDialog.open(viewer, framework, messages, ctx.scheduler(), text("worlds.gui.buttons.set-weather"),
                List.of(WeatherState.values()),
                state -> GuiItem.of(Material.WATER_BUCKET, text("worlds.gui.weather." + state.name().toLowerCase(Locale.ROOT))),
                (selectCtx, state) -> WorldsGuiActions.runAction(
                        ctx, viewer, WorldActionIds.SET_WEATHER, new SetWorldWeatherInput(worldName, state), () -> this.open(viewer)));
    }

    private void promptDifficulty(Player viewer) {
        SelectionDialog.open(viewer, framework, messages, ctx.scheduler(), text("worlds.gui.buttons.set-difficulty"),
                List.of(Difficulty.values()),
                difficulty -> GuiItem.of(Material.IRON_SWORD, Component.text(difficulty.name())),
                (selectCtx, difficulty) -> WorldsGuiActions.runAction(ctx, viewer, WorldActionIds.SET_DIFFICULTY,
                        new SetWorldDifficultyInput(worldName, difficulty), () -> this.open(viewer)));
    }

    private Long parseLong(String value) {
        try {
            return value == null ? null : Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private GuiItem header(WorldProfileSnapshot snapshot) {
        return GuiItem.of(Material.GRASS_BLOCK, Component.text(snapshot.name(), NamedTextColor.GOLD),
                List.of(Component.text(snapshot.environment().name(), NamedTextColor.DARK_GRAY)));
    }

    private GuiItem field(String labelKey, String value) {
        return GuiItem.of(Material.PAPER, text(labelKey), List.of(Component.text(value, NamedTextColor.GRAY)));
    }
}

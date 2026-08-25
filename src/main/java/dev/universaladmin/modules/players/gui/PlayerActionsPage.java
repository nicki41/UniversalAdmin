package dev.universaladmin.modules.players.gui;

import dev.universaladmin.action.ActionId;
import dev.universaladmin.gui.AbstractGuiPage;
import dev.universaladmin.gui.GuiButton;
import dev.universaladmin.gui.GuiItem;
import dev.universaladmin.gui.GuiLayout;
import dev.universaladmin.gui.GuiPageId;
import dev.universaladmin.gui.GuiRenderContext;
import dev.universaladmin.gui.GuiTextInput;
import dev.universaladmin.gui.GuiView;
import dev.universaladmin.gui.SelectionDialog;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.modules.players.PlayerPermissions;
import dev.universaladmin.modules.players.action.PlayerActionIds;
import dev.universaladmin.modules.players.action.PlayerTargetInput;
import dev.universaladmin.modules.players.action.SetDoubleValueInput;
import dev.universaladmin.modules.players.action.SetFloatValueInput;
import dev.universaladmin.modules.players.action.SetGamemodeInput;
import dev.universaladmin.modules.players.action.SetIntValueInput;
import dev.universaladmin.modules.players.action.TeleportInput;
import dev.universaladmin.modules.players.action.TeleportKind;
import dev.universaladmin.permission.PermissionNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.DoubleConsumer;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * The consolidated "PLAYER ACTIONS" hub: teleport (6), gamemode (4),
 * numeric sets (4, each via {@link GuiTextInput}), toggles (3), speeds (2,
 * via {@link GuiTextInput}), heal/feed/extinguish (3) - 22 buttons in one
 * 36-slot content area, each independently permission-gated (hidden, not
 * disabled, when the viewer lacks that specific node - see {@code
 * PlayerPermissions}). Every button calls {@link
 * dev.universaladmin.action.ActionExecutor#execute}; nothing here mutates a
 * player directly.
 */
public final class PlayerActionsPage extends AbstractGuiPage {

    public static final GuiPageId ID = GuiPageId.core("players.actions");

    private final PlayerGuiContext ctx;
    private final UUID targetId;
    private final String targetName;

    public PlayerActionsPage(PlayerGuiContext ctx, UUID targetId, String targetName) {
        super(ID, ctx.framework(), ctx.messages());
        this.ctx = ctx;
        this.targetId = targetId;
        this.targetName = targetName;
    }

    @Override
    protected boolean refreshable() {
        return false;
    }

    @Override
    protected Component title(Player viewer) {
        return text("players.gui.actions.title", targetName);
    }

    @Override
    protected void renderContent(GuiRenderContext context) {
        GuiView view = context.view();
        Player viewer = context.viewer();
        Player target = Bukkit.getPlayer(targetId);
        List<GuiButton> buttons = new ArrayList<>();

        buttons.add(button(Material.GOLDEN_APPLE, "players.gui.actions.heal", PlayerPermissions.HEAL,
                clickCtx -> runAction(clickCtx.viewer(), PlayerActionIds.HEAL, new PlayerTargetInput(targetId))));
        buttons.add(button(Material.COOKED_BEEF, "players.gui.actions.feed", PlayerPermissions.FEED,
                clickCtx -> runAction(clickCtx.viewer(), PlayerActionIds.FEED, new PlayerTargetInput(targetId))));
        buttons.add(button(Material.WATER_BUCKET, "players.gui.actions.extinguish", PlayerPermissions.EXTINGUISH,
                clickCtx -> runAction(clickCtx.viewer(), PlayerActionIds.EXTINGUISH, new PlayerTargetInput(targetId))));

        buttons.add(button(Material.ENDER_PEARL, "players.gui.actions.teleport-to-player", PlayerPermissions.TELEPORT,
                clickCtx -> runAction(clickCtx.viewer(), PlayerActionIds.TELEPORT, TeleportInput.of(TeleportKind.ADMIN_TO_PLAYER, targetId))));
        buttons.add(button(Material.FISHING_ROD, "players.gui.actions.bring", PlayerPermissions.TELEPORT,
                clickCtx -> runAction(clickCtx.viewer(), PlayerActionIds.TELEPORT, TeleportInput.of(TeleportKind.BRING_TO_ADMIN, targetId))));
        buttons.add(button(Material.ENDER_EYE, "players.gui.actions.teleport-to-other", PlayerPermissions.TELEPORT,
                clickCtx -> promptPlayerToPlayer(clickCtx.viewer())));
        buttons.add(button(Material.COMPASS, "players.gui.actions.world-spawn", PlayerPermissions.TELEPORT,
                clickCtx -> runAction(clickCtx.viewer(), PlayerActionIds.TELEPORT, TeleportInput.of(TeleportKind.WORLD_SPAWN, targetId))));
        buttons.add(button(Material.RED_BED, "players.gui.actions.bed", PlayerPermissions.TELEPORT,
                clickCtx -> runAction(clickCtx.viewer(), PlayerActionIds.TELEPORT, TeleportInput.of(TeleportKind.BED_RESPAWN, targetId))));
        buttons.add(button(Material.MAP, "players.gui.actions.coordinates", PlayerPermissions.TELEPORT,
                clickCtx -> promptCoordinates(clickCtx.viewer())));

        buttons.add(gamemodeButton(Material.GRASS_BLOCK, GameMode.SURVIVAL));
        buttons.add(gamemodeButton(Material.DIAMOND_PICKAXE, GameMode.CREATIVE));
        buttons.add(gamemodeButton(Material.MAP, GameMode.ADVENTURE));
        buttons.add(gamemodeButton(Material.ENDER_EYE, GameMode.SPECTATOR));

        buttons.add(button(Material.REDSTONE, "players.gui.actions.set-health", PlayerPermissions.SET_HEALTH,
                clickCtx -> promptNumber(clickCtx.viewer(), "players.gui.actions.prompt-health",
                        v -> runAction(clickCtx.viewer(), PlayerActionIds.SET_HEALTH, new SetDoubleValueInput(targetId, v)))));
        buttons.add(button(Material.BREAD, "players.gui.actions.set-food", PlayerPermissions.SET_FOOD,
                clickCtx -> promptNumber(clickCtx.viewer(), "players.gui.actions.prompt-food",
                        v -> runAction(clickCtx.viewer(), PlayerActionIds.SET_FOOD, new SetIntValueInput(targetId, (int) v)))));
        buttons.add(button(Material.EXPERIENCE_BOTTLE, "players.gui.actions.set-xp", PlayerPermissions.SET_XP,
                clickCtx -> promptNumber(clickCtx.viewer(), "players.gui.actions.prompt-xp",
                        v -> runAction(clickCtx.viewer(), PlayerActionIds.SET_XP, new SetFloatValueInput(targetId, (float) (v / 100.0))))));
        buttons.add(button(Material.ENCHANTED_BOOK, "players.gui.actions.set-level", PlayerPermissions.SET_LEVEL,
                clickCtx -> promptNumber(clickCtx.viewer(), "players.gui.actions.prompt-level",
                        v -> runAction(clickCtx.viewer(), PlayerActionIds.SET_LEVEL, new SetIntValueInput(targetId, (int) v)))));

        buttons.add(toggleButton(Material.FEATHER, "players.gui.actions.toggle-fly", PlayerPermissions.FLY_TOGGLE,
                target == null ? null : target.getAllowFlight(),
                clickCtx -> runAction(clickCtx.viewer(), PlayerActionIds.FLY_TOGGLE, new PlayerTargetInput(targetId))));
        buttons.add(button(Material.SUGAR, "players.gui.actions.fly-speed", PlayerPermissions.FLY_SPEED,
                clickCtx -> promptNumber(clickCtx.viewer(), "players.gui.actions.prompt-fly-speed",
                        v -> runAction(clickCtx.viewer(), PlayerActionIds.FLY_SPEED, new SetFloatValueInput(targetId, (float) v)))));
        buttons.add(button(Material.SUGAR, "players.gui.actions.walk-speed", PlayerPermissions.WALK_SPEED,
                clickCtx -> promptNumber(clickCtx.viewer(), "players.gui.actions.prompt-walk-speed",
                        v -> runAction(clickCtx.viewer(), PlayerActionIds.WALK_SPEED, new SetFloatValueInput(targetId, (float) v)))));

        buttons.add(toggleButton(Material.GLOWSTONE_DUST, "players.gui.actions.toggle-glow", PlayerPermissions.GLOW,
                target == null ? null : target.isGlowing(),
                clickCtx -> runAction(clickCtx.viewer(), PlayerActionIds.GLOW_TOGGLE, new PlayerTargetInput(targetId))));
        buttons.add(toggleButton(Material.FEATHER, "players.gui.actions.toggle-gravity", PlayerPermissions.GRAVITY,
                target == null ? null : target.hasGravity(),
                clickCtx -> runAction(clickCtx.viewer(), PlayerActionIds.GRAVITY_TOGGLE, new PlayerTargetInput(targetId))));
        buttons.add(toggleButton(Material.IRON_BARS, "players.gui.actions.toggle-collision", PlayerPermissions.COLLISION,
                target == null ? null : target.isCollidable(),
                clickCtx -> runAction(clickCtx.viewer(), PlayerActionIds.COLLISION_TOGGLE, new PlayerTargetInput(targetId))));

        int slot = GuiLayout.CONTENT_START_SLOT;
        for (GuiButton button : buttons) {
            view.place(slot++, button, viewer);
        }
    }

    private GuiButton gamemodeButton(Material material, GameMode gamemode) {
        return button(material, "players.gui.actions.gamemode-" + gamemode.name().toLowerCase(Locale.ROOT),
                PlayerPermissions.GAMEMODE,
                clickCtx -> runAction(clickCtx.viewer(), PlayerActionIds.GAMEMODE, new SetGamemodeInput(targetId, gamemode)));
    }

    private GuiButton button(Material material, String labelKey, PermissionNode permission, GuiButton.ClickHandler handler) {
        return new GuiButton(GuiItem.of(material, text(labelKey)), permission, handler);
    }

    /** Like {@link #button}, but the label interpolates the toggle's live current state - {@code currentlyOn} is {@code null} when the target is offline (state unknowable, since these toggles read a live Bukkit property, not persisted state). */
    private GuiButton toggleButton(Material material, String labelKey, PermissionNode permission, Boolean currentlyOn, GuiButton.ClickHandler handler) {
        String status = messages.get(MessageKey.of(
                currentlyOn == null ? "players.gui.status.unavailable" : (currentlyOn ? "players.gui.status.on" : "players.gui.status.off")));
        return new GuiButton(GuiItem.of(material, text(labelKey, status)), permission, handler);
    }

    private void promptPlayerToPlayer(Player viewer) {
        List<Player> onlinePlayers = new ArrayList<>(Bukkit.getOnlinePlayers());
        SelectionDialog.open(viewer, framework, messages, ctx.scheduler(), text("players.gui.actions.select-reference"),
                onlinePlayers,
                p -> GuiItem.playerHead(p, Component.text(p.getName()), List.of()),
                (clickCtx, chosen) -> runAction(viewer, PlayerActionIds.TELEPORT, TeleportInput.toPlayer(targetId, chosen.getUniqueId())));
    }

    private void promptCoordinates(Player viewer) {
        GuiTextInput.request(viewer, text("players.gui.actions.title", targetName), text("players.gui.actions.prompt-coordinates"), "",
                text("gui.confirm"), text("gui.cancel"),
                submitted -> {
                    TeleportInput input = parseCoordinates(submitted);
                    if (input == null) {
                        PlayerGuiActions.notifyError(viewer, messages);
                        this.open(viewer);
                        return;
                    }
                    runAction(viewer, PlayerActionIds.TELEPORT, input);
                },
                () -> this.open(viewer));
    }

    private TeleportInput parseCoordinates(String submitted) {
        if (submitted == null) {
            return null;
        }
        String[] tokens = submitted.trim().split("\\s+");
        try {
            if (tokens.length == 4) {
                return TeleportInput.toCoordinates(targetId, tokens[0],
                        Double.parseDouble(tokens[1]), Double.parseDouble(tokens[2]), Double.parseDouble(tokens[3]));
            }
            if (tokens.length == 3) {
                return TeleportInput.toCoordinates(targetId, null,
                        Double.parseDouble(tokens[0]), Double.parseDouble(tokens[1]), Double.parseDouble(tokens[2]));
            }
        } catch (NumberFormatException ignored) {
            // falls through to null below
        }
        return null;
    }

    private void promptNumber(Player viewer, String promptKey, DoubleConsumer onValue) {
        GuiTextInput.request(viewer, text("players.gui.actions.title", targetName), text(promptKey), "",
                text("gui.confirm"), text("gui.cancel"),
                submitted -> {
                    try {
                        onValue.accept(Double.parseDouble(submitted.trim()));
                    } catch (NumberFormatException | NullPointerException e) {
                        PlayerGuiActions.notifyError(viewer, messages);
                        this.open(viewer);
                    }
                },
                () -> this.open(viewer));
    }

    private <I> void runAction(Player viewer, ActionId id, I input) {
        ctx.actionExecutor().<I, Object>execute(id, PlayerGuiActions.contextFor(viewer), input)
                .whenComplete((result, error) -> ctx.scheduler().runOnMainThread(() -> {
                    if (!viewer.isOnline()) {
                        return;
                    }
                    if (error != null) {
                        PlayerGuiActions.notifyError(viewer, messages);
                    } else {
                        PlayerGuiActions.notifyResult(viewer, messages, result);
                    }
                    this.open(viewer);
                }));
    }
}

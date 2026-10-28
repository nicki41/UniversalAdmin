package dev.universaladmin.modules.players.gui;

import dev.universaladmin.action.ActionId;
import dev.universaladmin.gui.AbstractGuiPage;
import dev.universaladmin.gui.GuiButton;
import dev.universaladmin.gui.GuiForm;
import dev.universaladmin.gui.GuiItem;
import dev.universaladmin.gui.GuiLayout;
import dev.universaladmin.gui.GuiPage;
import dev.universaladmin.gui.GuiPageId;
import dev.universaladmin.gui.GuiRenderContext;
import dev.universaladmin.gui.GuiTextInput;
import dev.universaladmin.gui.GuiView;
import dev.universaladmin.gui.SelectionDialog;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.localization.MessageService;
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
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
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
    private GuiPage returnTo;

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
                clickCtx -> promptSlider(clickCtx.viewer(), "players.gui.actions.prompt-health", 0, maxHealth(), 0.5f,
                        current(p -> (float) p.getHealth(), 20),
                        v -> runAction(clickCtx.viewer(), PlayerActionIds.SET_HEALTH, new SetDoubleValueInput(targetId, v)))));
        buttons.add(button(Material.BREAD, "players.gui.actions.set-food", PlayerPermissions.SET_FOOD,
                clickCtx -> promptSlider(clickCtx.viewer(), "players.gui.actions.prompt-food", 0, 20, 1,
                        current(p -> (float) p.getFoodLevel(), 20),
                        v -> runAction(clickCtx.viewer(), PlayerActionIds.SET_FOOD, new SetIntValueInput(targetId, (int) Math.round(v))))));
        buttons.add(button(Material.EXPERIENCE_BOTTLE, "players.gui.actions.set-xp", PlayerPermissions.SET_XP,
                clickCtx -> promptSlider(clickCtx.viewer(), "players.gui.actions.prompt-xp", 0, 100, 1,
                        current(p -> p.getExp() * 100, 0),
                        v -> runAction(clickCtx.viewer(), PlayerActionIds.SET_XP, new SetFloatValueInput(targetId, (float) (v / 100.0))))));
        buttons.add(button(Material.ENCHANTED_BOOK, "players.gui.actions.set-level", PlayerPermissions.SET_LEVEL,
                clickCtx -> promptNumber(clickCtx.viewer(), "players.gui.actions.prompt-level",
                        v -> runAction(clickCtx.viewer(), PlayerActionIds.SET_LEVEL, new SetIntValueInput(targetId, (int) v)))));

        buttons.add(toggleButton(Material.FEATHER, "players.gui.actions.toggle-fly", PlayerPermissions.FLY_TOGGLE,
                target == null ? null : target.getAllowFlight(),
                clickCtx -> runAction(clickCtx.viewer(), PlayerActionIds.FLY_TOGGLE, new PlayerTargetInput(targetId))));
        buttons.add(button(Material.SUGAR, "players.gui.actions.fly-speed", PlayerPermissions.FLY_SPEED,
                clickCtx -> promptSlider(clickCtx.viewer(), "players.gui.actions.prompt-fly-speed", -1, 1, 0.05f,
                        current(p -> p.getFlySpeed(), 0.1f),
                        v -> runAction(clickCtx.viewer(), PlayerActionIds.FLY_SPEED, new SetFloatValueInput(targetId, (float) v)))));
        buttons.add(button(Material.SUGAR, "players.gui.actions.walk-speed", PlayerPermissions.WALK_SPEED,
                clickCtx -> promptSlider(clickCtx.viewer(), "players.gui.actions.prompt-walk-speed", -1, 1, 0.05f,
                        current(p -> p.getWalkSpeed(), 0.2f),
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

    /**
     * The profile's quick help row: teleport there, bring here, heal, feed,
     * flight - {@code target} online here. After a click {@code returnTo} is
     * shown again, not this page.
     */
    List<GuiButton> quickButtons(Player target, GuiPage returnTo) {
        this.returnTo = returnTo;
        return List.of(
                button(Material.ENDER_PEARL, "players.gui.actions.teleport-to-player", PlayerPermissions.TELEPORT,
                        clickCtx -> runAction(clickCtx.viewer(), PlayerActionIds.TELEPORT, TeleportInput.of(TeleportKind.ADMIN_TO_PLAYER, targetId))),
                button(Material.FISHING_ROD, "players.gui.actions.bring", PlayerPermissions.TELEPORT,
                        clickCtx -> runAction(clickCtx.viewer(), PlayerActionIds.TELEPORT, TeleportInput.of(TeleportKind.BRING_TO_ADMIN, targetId))),
                button(Material.GOLDEN_APPLE, "players.gui.actions.heal", PlayerPermissions.HEAL,
                        clickCtx -> runAction(clickCtx.viewer(), PlayerActionIds.HEAL, new PlayerTargetInput(targetId))),
                button(Material.COOKED_BEEF, "players.gui.actions.feed", PlayerPermissions.FEED,
                        clickCtx -> runAction(clickCtx.viewer(), PlayerActionIds.FEED, new PlayerTargetInput(targetId))),
                toggleButton(Material.FEATHER, "players.gui.actions.toggle-fly", PlayerPermissions.FLY_TOGGLE,
                        target == null ? null : target.getAllowFlight(),
                        clickCtx -> runAction(clickCtx.viewer(), PlayerActionIds.FLY_TOGGLE, new PlayerTargetInput(targetId))));
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
        return new GuiButton(GuiItem.of(material, text(labelKey, MessageService.markup(status))), permission, handler);
    }

    private void promptPlayerToPlayer(Player viewer) {
        List<Player> onlinePlayers = new ArrayList<>(Bukkit.getOnlinePlayers());
        SelectionDialog.open(viewer, framework, messages, ctx.scheduler(), text("players.gui.actions.select-reference"),
                onlinePlayers,
                p -> GuiItem.playerHead(p, Component.text(p.getName()), List.of()),
                (clickCtx, chosen) -> runAction(viewer, PlayerActionIds.TELEPORT, TeleportInput.toPlayer(targetId, chosen.getUniqueId())));
    }

    /**
     * x, y and z as fields and the world from a list (ADR 0023), filled in
     * with where the player is now - or the viewer, if the player is not here.
     */
    private void promptCoordinates(Player viewer) {
        Player target = Bukkit.getPlayer(targetId);
        Location from = target != null ? target.getLocation() : viewer.getLocation();
        List<GuiForm.Option> worlds = Bukkit.getWorlds().stream()
                .map(world -> new GuiForm.Option(world.getName(), Component.text(world.getName())))
                .toList();
        GuiForm.builder(text("players.gui.actions.title", targetName))
                .choice("world", text("players.gui.actions.prompt-world"), worlds, from.getWorld().getName())
                .text("x", text("players.gui.actions.prompt-x"), "%.1f".formatted(from.getX()), 16)
                .text("y", text("players.gui.actions.prompt-y"), "%.1f".formatted(from.getY()), 16)
                .text("z", text("players.gui.actions.prompt-z"), "%.1f".formatted(from.getZ()), 16)
                .open(viewer, text("gui.confirm"), text("gui.cancel"), response -> {
                    try {
                        runAction(viewer, PlayerActionIds.TELEPORT, TeleportInput.toCoordinates(targetId, response.choice("world"),
                                coordinate(response.text("x")), coordinate(response.text("y")), coordinate(response.text("z"))));
                    } catch (NumberFormatException e) {
                        PlayerGuiActions.notifyError(viewer, messages);
                        this.open(viewer);
                    }
                }, () -> this.open(viewer));
    }

    /** A coordinate as typed - a comma works as the decimal point too, as German keyboards suggest. */
    private static double coordinate(String typed) {
        return Double.parseDouble(typed.trim().replace(',', '.'));
    }

    /** A bounded value as a slider, starting at the player's current value (ADR 0023). */
    private void promptSlider(Player viewer, String promptKey, float min, float max, float step, float initial, DoubleConsumer onValue) {
        GuiForm.builder(text("players.gui.actions.title", targetName))
                .number("value", text(promptKey), min, max, step, initial)
                .open(viewer, text("gui.confirm"), text("gui.cancel"),
                        response -> onValue.accept(response.number("value")),
                        () -> this.open(viewer));
    }

    /** The player's current value, or {@code fallback} if they are not on this server. */
    private float current(java.util.function.Function<Player, Float> value, float fallback) {
        Player target = Bukkit.getPlayer(targetId);
        return target == null ? fallback : value.apply(target);
    }

    /** The player's maximum health - attributes can raise it above 20. */
    private float maxHealth() {
        Player target = Bukkit.getPlayer(targetId);
        if (target == null) {
            return 20;
        }
        AttributeInstance attribute = target.getAttribute(Attribute.MAX_HEALTH);
        return attribute == null ? 20 : (float) attribute.getValue();
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
                    (returnTo != null ? returnTo : this).open(viewer);
                }));
    }
}

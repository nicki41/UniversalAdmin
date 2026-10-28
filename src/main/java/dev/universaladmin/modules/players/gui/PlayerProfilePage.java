package dev.universaladmin.modules.players.gui;

import dev.universaladmin.action.ActionResult;
import dev.universaladmin.core.PlayerRef;
import dev.universaladmin.gui.AbstractGuiPage;
import dev.universaladmin.gui.GuiButton;
import dev.universaladmin.gui.GuiItem;
import dev.universaladmin.gui.GuiLayout;
import dev.universaladmin.gui.GuiPageId;
import dev.universaladmin.gui.GuiRenderContext;
import dev.universaladmin.gui.GuiView;
import dev.universaladmin.gui.PlayerMenuEntry;
import dev.universaladmin.modules.players.PlayerPermissions;
import dev.universaladmin.modules.players.PlayerSnapshot;
import dev.universaladmin.modules.players.action.GetPlayerIpAddressAction;
import dev.universaladmin.modules.players.action.PlayerTargetInput;
import dev.universaladmin.network.NetworkPlayer;
import dev.universaladmin.network.NetworkVisibility;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * The Profile page: every field requested for "PLAYER PROFILE" (soweit
 * verfügbar - an offline target simply omits whatever needs live entity
 * state, see {@link PlayerSnapshot}) plus navigation into Actions/Effects/
 * Inventory/Ender Chest. Ephemeral like {@code AuditLogDetailPage} - built
 * fresh per click with the target baked in via the constructor, never
 * registered in {@code GuiRegistry}.
 */
public final class PlayerProfilePage extends AbstractGuiPage {

    public static final GuiPageId ID = GuiPageId.core("players.profile");

    private final PlayerGuiContext ctx;
    private final UUID targetId;
    private final String targetName;

    public PlayerProfilePage(PlayerGuiContext ctx, UUID targetId, String targetName) {
        super(ID, ctx.framework(), ctx.messages());
        this.ctx = ctx;
        this.targetId = targetId;
        this.targetName = targetName;
    }

    @Override
    protected Component title(Player viewer) {
        return Component.text(targetName);
    }

    @Override
    protected void renderContent(GuiRenderContext context) {
        renderPlaceholder(context.view(), framework.icons().loading(), text("gui.loading"));
        Player viewer = context.viewer();

        ctx.playerService().snapshot(targetId)
                .thenCompose(snapshot -> fetchIpIfPermitted(viewer, snapshot))
                .whenComplete((result, error) -> ctx.scheduler().runOnMainThread(() -> {
                    if (!stillOpen(context)) {
                        return;
                    }
                    if (error != null) {
                        renderPlaceholder(context.view(), framework.icons().error(), text("gui.error"));
                        return;
                    }
                    if (result.snapshot().isEmpty()) {
                        renderPlaceholder(context.view(), framework.icons().empty(), text("players.gui.not-found"));
                        return;
                    }
                    renderProfile(context, result.snapshot().get(), result.ip());
                }));
    }

    private CompletableFuture<Result> fetchIpIfPermitted(Player viewer, Optional<PlayerSnapshot> snapshot) {
        boolean eligible = snapshot.isPresent() && snapshot.get().online() && viewer.hasPermission(PlayerPermissions.IP.value());
        if (!eligible) {
            return CompletableFuture.completedFuture(new Result(snapshot, null));
        }
        return ctx.actionExecutor()
                .<PlayerTargetInput, String>execute(GetPlayerIpAddressAction.ID, PlayerGuiActions.contextFor(viewer), new PlayerTargetInput(targetId))
                .thenApply(ipResult -> new Result(snapshot, ipResult instanceof ActionResult.Success<String> success ? success.value() : null));
    }

    private void renderProfile(GuiRenderContext context, PlayerSnapshot snapshot, String ip) {
        GuiView view = context.view();
        Player viewer = context.viewer();
        view.clearContentArea();

        List<GuiItem> fields = new ArrayList<>();
        fields.add(header(snapshot));
        Optional<NetworkPlayer> elsewhere = elsewhere(viewer);
        if (ctx.network().enabled() && (snapshot.online() || elsewhere.isPresent())) {
            fields.add(field("players.gui.field.server", elsewhere.map(NetworkPlayer::server).orElse(ctx.network().serverName())));
        }
        fields.add(field("players.gui.field.first-join", PlayerGuiFormat.instant(snapshot.firstJoin())));
        fields.add(field("players.gui.field.last-join", PlayerGuiFormat.instant(snapshot.lastSeen())));
        if (snapshot.sessionDuration() != null) {
            fields.add(field("players.gui.field.session-duration", PlayerGuiFormat.duration(snapshot.sessionDuration())));
        }
        fields.add(field("players.gui.field.total-playtime", PlayerGuiFormat.duration(snapshot.totalPlaytime())));
        if (snapshot.world() != null) {
            fields.add(field("players.gui.field.world", snapshot.world()));
            fields.add(field("players.gui.field.coordinates", PlayerGuiFormat.coordinates(snapshot.x(), snapshot.y(), snapshot.z())));
        }
        if (snapshot.gamemode() != null) {
            fields.add(field("players.gui.field.gamemode", snapshot.gamemode().name()));
        }
        if (snapshot.health() != null) {
            fields.add(field("players.gui.field.health", "%.1f / %.1f".formatted(snapshot.health(), snapshot.maxHealth())));
        }
        if (snapshot.food() != null) {
            fields.add(field("players.gui.field.food", String.valueOf(snapshot.food())));
        }
        if (snapshot.saturation() != null) {
            fields.add(field("players.gui.field.saturation", "%.1f".formatted(snapshot.saturation())));
        }
        if (snapshot.experienceProgress() != null) {
            fields.add(field("players.gui.field.xp", "%.0f%%".formatted(snapshot.experienceProgress() * 100)));
        }
        if (snapshot.level() != null) {
            fields.add(field("players.gui.field.level", String.valueOf(snapshot.level())));
        }
        if (snapshot.ping() != null) {
            fields.add(field("players.gui.field.ping", snapshot.ping() + "ms"));
        }
        if (snapshot.locale() != null) {
            fields.add(field("players.gui.field.locale", snapshot.locale()));
        }
        fields.add(field("players.gui.field.effects",
                snapshot.activeEffects().isEmpty() ? "-" : String.join(", ", snapshot.activeEffects())));
        if (snapshot.respawnLocation() != null) {
            fields.add(field("players.gui.field.respawn-location", snapshot.respawnLocation()));
        }
        if (ip != null) {
            fields.add(field("players.gui.field.ip", ip));
        }

        for (int i = 0; i < fields.size() && i < GuiLayout.contentSize(); i++) {
            view.place(GuiLayout.contentSlot(i), fields.get(i));
        }

        placeNavigationButtons(view, viewer, snapshot);
    }

    /**
     * The profile's button rows: the Players module's own pages first, then
     * every {@link PlayerMenuEntry} other modules and extensions contributed
     * (e.g. Moderation's "Moderate"), in their declared order. Starts at the
     * last content row and continues into the bottom row this page doesn't
     * otherwise use.
     */
    private void placeNavigationButtons(GuiView view, Player viewer, PlayerSnapshot snapshot) {
        List<GuiButton> buttons = new ArrayList<>();
        buttons.add(GuiButton.of(GuiItem.of(Material.COMMAND_BLOCK, text("players.gui.profile.actions")),
                clickCtx -> clickCtx.open(new PlayerActionsPage(ctx, targetId, targetName))));
        buttons.add(GuiButton.of(GuiItem.of(Material.BREWING_STAND, text("players.gui.profile.effects")),
                clickCtx -> clickCtx.open(new PlayerEffectsPage(ctx, targetId, targetName))));
        // Inventory/Ender Chest need a live PlayerInventory - hidden (not
        // disabled) when the target is offline, per "hide (don't disable) a
        // GUI button whose action needs an online player".
        if (snapshot.online()) {
            buttons.add(new GuiButton(GuiItem.of(Material.CHEST, text("players.gui.profile.inventory")),
                    PlayerPermissions.INVENTORY_VIEW,
                    clickCtx -> clickCtx.open(new PlayerInventoryPage(ctx, targetId, targetName))));
            buttons.add(new GuiButton(GuiItem.of(Material.ENDER_CHEST, text("players.gui.profile.enderchest")),
                    PlayerPermissions.ENDERCHEST_VIEW,
                    clickCtx -> clickCtx.open(new PlayerEnderChestPage(ctx, targetId, targetName))));
        }
        PlayerRef target = new PlayerRef(targetId, targetName);
        for (PlayerMenuEntry entry : ctx.playerMenu().entries()) {
            if (entry.requiresOnline() && !snapshot.online() || !entry.visible(viewer, target)) {
                continue;
            }
            buttons.add(new GuiButton(entry.icon(viewer, target), entry.permission(), clickCtx -> entry.onClick(clickCtx, target)));
        }

        int slot = GuiLayout.CONTENT_END_SLOT - (GuiLayout.COLUMNS - 1);
        for (GuiButton button : buttons) {
            if (slot >= GuiLayout.SIZE) {
                break;
            }
            if (button.isVisibleTo(viewer)) {
                view.place(slot++, button, viewer);
            }
        }
    }

    /** Where the player is, if they are online on another server of the network - and the viewer may see them there. */
    private Optional<NetworkPlayer> elsewhere(Player viewer) {
        if (!ctx.network().enabled()) {
            return Optional.empty();
        }
        return ctx.network().player(targetId)
                .filter(player -> !player.server().equalsIgnoreCase(ctx.network().serverName()))
                .filter(player -> NetworkVisibility.canSee(viewer, ctx.network(), player));
    }

    private GuiItem header(PlayerSnapshot snapshot) {
        Optional<NetworkPlayer> elsewhere = snapshot.online() ? Optional.empty() : ctx.network().player(targetId)
                .filter(player -> ctx.network().enabled() && !player.server().equalsIgnoreCase(ctx.network().serverName()));
        if (elsewhere.isPresent()) {
            List<Component> lore = List.of(text("players.gui.field.status-elsewhere", elsewhere.get().server()),
                    Component.text(snapshot.id().toString(), NamedTextColor.DARK_GRAY));
            return GuiItem.playerHead(Bukkit.getOfflinePlayer(snapshot.id()), Component.text(targetName, NamedTextColor.GOLD), lore);
        }
        Component statusKey = text(snapshot.online() ? "players.gui.field.status-online" : "players.gui.field.status-offline");
        List<Component> lore = List.of(statusKey, Component.text(snapshot.id().toString(), NamedTextColor.DARK_GRAY));
        if (snapshot.online()) {
            return GuiItem.playerHead(Bukkit.getOfflinePlayer(snapshot.id()), Component.text(targetName, NamedTextColor.GOLD), lore);
        }
        return GuiItem.of(Material.SKELETON_SKULL, Component.text(targetName, NamedTextColor.GOLD), lore);
    }

    private GuiItem field(String labelKey, String value) {
        return GuiItem.of(Material.PAPER, text(labelKey), List.of(Component.text(value, NamedTextColor.GRAY)));
    }

    private void renderPlaceholder(GuiView view, Material material, Component label) {
        view.clearContentArea();
        view.place(GuiLayout.contentSlot(GuiLayout.contentSize() / 2), GuiItem.of(material, label));
    }

    private boolean stillOpen(GuiRenderContext context) {
        Player viewer = context.viewer();
        return context.view().isOpenFor(viewer);
    }

    private record Result(Optional<PlayerSnapshot> snapshot, String ip) {
    }
}

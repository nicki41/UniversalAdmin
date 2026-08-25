package dev.universaladmin.modules.players.gui;

import dev.universaladmin.action.ActionResult;
import dev.universaladmin.gui.AbstractGuiPage;
import dev.universaladmin.gui.GuiButton;
import dev.universaladmin.gui.GuiItem;
import dev.universaladmin.gui.GuiLayout;
import dev.universaladmin.gui.GuiPageId;
import dev.universaladmin.gui.GuiRenderContext;
import dev.universaladmin.gui.GuiView;
import dev.universaladmin.modules.moderation.ModerationPlayerLink;
import dev.universaladmin.modules.players.PlayerPermissions;
import dev.universaladmin.modules.players.PlayerSnapshot;
import dev.universaladmin.modules.players.action.GetPlayerIpAddressAction;
import dev.universaladmin.modules.players.action.PlayerTargetInput;
import dev.universaladmin.permission.PermissionNode;
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

    private void placeNavigationButtons(GuiView view, Player viewer, PlayerSnapshot snapshot) {
        int slot = GuiLayout.CONTENT_END_SLOT - 4;
        slot = placeModerateButton(view, viewer, slot);
        view.place(slot++, GuiButton.of(GuiItem.of(Material.COMMAND_BLOCK, text("players.gui.profile.actions")),
                clickCtx -> clickCtx.open(new PlayerActionsPage(ctx, targetId, targetName))), viewer);
        view.place(slot++, GuiButton.of(GuiItem.of(Material.BREWING_STAND, text("players.gui.profile.effects")),
                clickCtx -> clickCtx.open(new PlayerEffectsPage(ctx, targetId, targetName))), viewer);
        // Inventory/Ender Chest need a live PlayerInventory - hidden (not
        // disabled) when the target is offline, per "GUI disabled/hidden
        // wenn Action Online Player benötigt".
        if (snapshot.online()) {
            if (viewer.hasPermission(PlayerPermissions.INVENTORY_VIEW.value())) {
                view.place(slot++, GuiButton.of(GuiItem.of(Material.CHEST, text("players.gui.profile.inventory")),
                        clickCtx -> clickCtx.open(new PlayerInventoryPage(ctx, targetId, targetName))), viewer);
            }
            if (viewer.hasPermission(PlayerPermissions.ENDERCHEST_VIEW.value())) {
                view.place(slot, GuiButton.of(GuiItem.of(Material.ENDER_CHEST, text("players.gui.profile.enderchest")),
                        clickCtx -> clickCtx.open(new PlayerEnderChestPage(ctx, targetId, targetName))), viewer);
            }
        }
    }

    /**
     * Optional "Moderate" button - present only if the Moderation module is
     * enabled and published a {@link ModerationPlayerLink} (see that
     * interface's javadoc for why this is a {@code ServiceRegistry} lookup
     * rather than a direct import of anything moderation-internal). Returns
     * the next free slot, whether or not the button was actually placed.
     */
    private int placeModerateButton(GuiView view, Player viewer, int slot) {
        Optional<ModerationPlayerLink> link = ctx.services().get(ModerationPlayerLink.class);
        if (link.isEmpty() || !viewer.hasPermission(PermissionNode.core("moderation.use").value())) {
            return slot;
        }
        view.place(slot, GuiButton.of(GuiItem.of(Material.IRON_SWORD, text("players.gui.profile.moderate")),
                clickCtx -> clickCtx.open(link.get().moderationPage(targetId, targetName))), viewer);
        return slot + 1;
    }

    private GuiItem header(PlayerSnapshot snapshot) {
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
        return viewer.isOnline() && viewer.getOpenInventory().getTopInventory().getHolder() == context.view();
    }

    private record Result(Optional<PlayerSnapshot> snapshot, String ip) {
    }
}

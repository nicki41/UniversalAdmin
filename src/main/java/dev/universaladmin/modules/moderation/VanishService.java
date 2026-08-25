package dev.universaladmin.modules.moderation;

import dev.universaladmin.action.Actor;
import dev.universaladmin.localization.ComponentMessages;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.localization.MessageService;
import dev.universaladmin.permission.bukkit.PermissiblePermissionEvaluator;
import dev.universaladmin.settings.SettingsService;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Applies/reverses vanish visibility - every method that touches Bukkit
 * visibility APIs must run on the main thread (documented per-method, same
 * convention as {@link CollisionState#refresh}); persistence
 * ({@link VanishRepository}) is the only async half, used purely for
 * reconnect-restore, never consulted for the actual hide/show decision
 * (that's {@link VanishRuntimeState}, see its javadoc).
 */
public final class VanishService {

    private final VanishRepository repository;
    private final VanishRuntimeState runtimeState;
    private final VanishVisibilityPolicy policy;
    private final CollisionState collisionState;
    private final StaffModeState staffModeState;
    private final MessageService messages;
    private final SettingsService settings;
    private final Plugin plugin;

    public VanishService(
            VanishRepository repository, VanishRuntimeState runtimeState, VanishVisibilityPolicy policy,
            CollisionState collisionState, StaffModeState staffModeState, MessageService messages,
            SettingsService settings, Plugin plugin) {
        this.repository = repository;
        this.runtimeState = runtimeState;
        this.policy = policy;
        this.collisionState = collisionState;
        this.staffModeState = staffModeState;
        this.messages = messages;
        this.settings = settings;
        this.plugin = plugin;
    }

    public boolean isVanished(UUID playerId) {
        return runtimeState.isVanished(playerId);
    }

    /**
     * Toggles vanish for {@code player} via an explicit staff action:
     * applies visibility, persists the new state, and - unlike {@link
     * #apply}, which {@code VanishReconnectListener}/{@code StaffModeService}
     * also call for non-toggle reasons (reconnect-restore, staff-mode
     * auto-vanish) - is the only path that fires the optional fake
     * leave/join broadcast, since that's meant to react to a real toggle,
     * not every time visibility happens to get (re)synced. Main thread only.
     */
    public CompletableFuture<Boolean> toggle(Player player) {
        boolean newState = !runtimeState.isVanished(player.getUniqueId());
        apply(player, newState);
        if (newState && settings.get(ModerationSettings.VANISH_FAKE_LEAVE_ON_VANISH)) {
            broadcastFake(player, MessageKey.of("moderation.enforcement.fake-leave"));
        } else if (!newState && settings.get(ModerationSettings.VANISH_FAKE_JOIN_ON_UNVANISH)) {
            broadcastFake(player, MessageKey.of("moderation.enforcement.fake-join"));
        }
        if (newState) {
            return repository.save(VanishRecord.now(player.getUniqueId())).thenApply(ignored -> true);
        }
        return repository.deleteById(player.getUniqueId()).thenApply(ignored -> false);
    }

    /** Hides/shows {@code player} for every currently-online viewer without the bypass permission. Main thread only. */
    public void apply(Player player, boolean vanished) {
        runtimeState.setVanished(player.getUniqueId(), vanished);
        player.setVisibleByDefault(!vanished);
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer.getUniqueId().equals(player.getUniqueId())) {
                continue;
            }
            if (canSee(viewer, player)) {
                continue;
            }
            if (vanished) {
                viewer.hidePlayer(plugin, player);
                viewer.unlistPlayer(player);
            } else {
                viewer.showPlayer(plugin, player);
                viewer.listPlayer(player);
            }
        }
        collisionState.refresh(player, vanished, staffModeState.isActive(player.getUniqueId())
                && settings.get(ModerationSettings.STAFFMODE_AUTO_NOCOLLISION));
    }

    /** Shows every currently-vanished online player to {@code viewer}, if they hold the bypass permission. Main thread only. */
    public void revealAllTo(Player viewer) {
        for (UUID vanishedId : runtimeState.all()) {
            Player vanishedPlayer = Bukkit.getPlayer(vanishedId);
            if (vanishedPlayer == null || vanishedPlayer.getUniqueId().equals(viewer.getUniqueId())) {
                continue;
            }
            if (canSee(viewer, vanishedPlayer)) {
                viewer.showPlayer(plugin, vanishedPlayer);
                viewer.listPlayer(vanishedPlayer);
            }
        }
    }

    /** Clears the runtime (online-only) flag - called on quit, since a disconnected player is trivially "not currently vanished". */
    public void clearRuntimeState(UUID playerId) {
        runtimeState.setVanished(playerId, false);
    }

    public CompletableFuture<Boolean> hasPersistedVanish(UUID playerId) {
        return repository.findById(playerId).thenApply(Optional::isPresent);
    }

    public boolean restoreOnReconnectEnabled() {
        return settings.get(ModerationSettings.VANISH_RESTORE_ON_RECONNECT);
    }

    public boolean hideJoinMessageEnabled() {
        return settings.get(ModerationSettings.VANISH_HIDE_JOIN_MESSAGE);
    }

    public boolean hideQuitMessageEnabled() {
        return settings.get(ModerationSettings.VANISH_HIDE_QUIT_MESSAGE);
    }

    private boolean canSee(Player viewer, Player vanishedPlayer) {
        Actor viewerActor = Actor.player(viewer.getUniqueId(), viewer.getName(), new PermissiblePermissionEvaluator(viewer));
        return policy.canSee(viewerActor, vanishedPlayer.getUniqueId(), VanishLevel.STANDARD);
    }

    private void broadcastFake(Player player, MessageKey key) {
        Component message = ComponentMessages.render(messages.get(key, player.getName()));
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (!viewer.getUniqueId().equals(player.getUniqueId()) && !viewer.hasPermission(ModerationPermissions.BYPASS_VANISH.value())) {
                viewer.sendMessage(message);
            }
        }
    }
}

package dev.universaladmin.modules.moderation;

import dev.universaladmin.scheduler.TaskScheduler;
import dev.universaladmin.settings.SettingsService;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * Orchestrates Enter/Exit/Recover. The crash-safety requirement ("Staff
 * Snapshot persistent speichern, bevor Player Inventory verändert wird") is
 * an ordering property of {@link #enter}, not a flag anywhere: the
 * inventory is never cleared until the snapshot's {@code save} future has
 * already resolved successfully, so a crash between those two steps simply
 * never mutates anything - the player keeps their real inventory and next
 * login has no snapshot to recover (nothing was lost, nothing needs
 * recovering). "Wenn Snapshot bereits existiert: nicht blind überschreiben"
 * is the {@code repository.findById} check at the top of {@link #enter}.
 */
public final class StaffModeService {

    /** Combined snapshot array layout: 36 storage + helmet, chestplate, leggings, boots, offhand. */
    private static final int EQUIPMENT_SLOTS = 5;

    public enum RecoveryOutcome {
        RECOVERED,
        NOT_ONLINE,
        NO_SNAPSHOT
    }

    private final StaffModeSnapshotRepository repository;
    private final StaffModeState state;
    private final GodmodeState godmodeState;
    private final CollisionState collisionState;
    private final VanishService vanishService;
    private final StaffToolItems toolItems;
    private final SettingsService settings;
    private final TaskScheduler scheduler;

    public StaffModeService(
            StaffModeSnapshotRepository repository, StaffModeState state, GodmodeState godmodeState,
            CollisionState collisionState, VanishService vanishService, StaffToolItems toolItems,
            SettingsService settings, TaskScheduler scheduler) {
        this.repository = repository;
        this.state = state;
        this.godmodeState = godmodeState;
        this.collisionState = collisionState;
        this.vanishService = vanishService;
        this.toolItems = toolItems;
        this.settings = settings;
        this.scheduler = scheduler;
    }

    public boolean isActive(UUID playerId) {
        return state.isActive(playerId);
    }

    /** {@code true} if entered, {@code false} if a snapshot already exists (refused - see class javadoc). */
    public CompletableFuture<Boolean> enter(Player player) {
        UUID id = player.getUniqueId();
        return repository.findById(id).thenCompose(existing -> {
            if (existing.isPresent()) {
                return CompletableFuture.completedFuture(false);
            }
            CompletableFuture<Boolean> result = new CompletableFuture<>();
            scheduler.runOnMainThread(() -> {
                StaffModeSnapshot snapshot = buildSnapshot(player);
                // The inventory is not touched until THIS future resolves - see class javadoc.
                repository.save(snapshot).whenComplete((saved, error) -> {
                    if (error != null) {
                        result.completeExceptionally(error);
                        return;
                    }
                    scheduler.runOnMainThread(() -> {
                        applyEntry(player);
                        result.complete(true);
                    });
                });
            });
            return result;
        });
    }

    /** {@code true} if a snapshot was restored, {@code false} if there was none to restore. */
    public CompletableFuture<Boolean> exit(Player player) {
        UUID id = player.getUniqueId();
        return repository.findById(id).thenCompose(snapshotOpt -> {
            if (snapshotOpt.isEmpty()) {
                return CompletableFuture.completedFuture(false);
            }
            CompletableFuture<Boolean> result = new CompletableFuture<>();
            scheduler.runOnMainThread(() -> {
                restoreFromSnapshot(player, snapshotOpt.get());
                repository.deleteById(id).whenComplete((ignoredVoid, error) -> result.complete(true));
            });
            return result;
        });
    }

    /**
     * Same operation as {@link #exit}, by target id - the recovery
     * command/listener need to distinguish "not online" from "nothing to
     * recover". Resolves the target on the main thread, like every other
     * {@code Bukkit.getPlayer} lookup in this codebase.
     */
    public CompletableFuture<RecoveryOutcome> recover(UUID targetId) {
        CompletableFuture<RecoveryOutcome> future = new CompletableFuture<>();
        scheduler.runOnMainThread(() -> {
            Player player = Bukkit.getPlayer(targetId);
            if (player == null) {
                future.complete(RecoveryOutcome.NOT_ONLINE);
                return;
            }
            exit(player).whenComplete((restored, error) -> {
                if (error != null) {
                    future.completeExceptionally(error);
                } else {
                    future.complete(restored ? RecoveryOutcome.RECOVERED : RecoveryOutcome.NO_SNAPSHOT);
                }
            });
        });
        return future;
    }

    private StaffModeSnapshot buildSnapshot(Player player) {
        byte[] data = ItemStack.serializeItemsAsBytes(combinedContents(player.getInventory()));
        return new StaffModeSnapshot(
                player.getUniqueId(), data, player.getGameMode(), player.getExp(), player.getLevel(),
                player.getAllowFlight(), player.isFlying(), Instant.now());
    }

    private static ItemStack[] combinedContents(PlayerInventory inventory) {
        ItemStack[] storage = inventory.getStorageContents();
        ItemStack[] combined = new ItemStack[storage.length + EQUIPMENT_SLOTS];
        System.arraycopy(storage, 0, combined, 0, storage.length);
        combined[storage.length] = inventory.getHelmet();
        combined[storage.length + 1] = inventory.getChestplate();
        combined[storage.length + 2] = inventory.getLeggings();
        combined[storage.length + 3] = inventory.getBoots();
        combined[storage.length + 4] = inventory.getItemInOffHand();
        return combined;
    }

    private void applyEntry(Player player) {
        UUID id = player.getUniqueId();
        state.setActive(id, true);

        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        inventory.setHelmet(null);
        inventory.setChestplate(null);
        inventory.setLeggings(null);
        inventory.setBoots(null);
        inventory.setItemInOffHand(null);
        toolItems.giveKit(player);

        // Unconditional per the spec: staff mode always blocks damage while
        // active (StaffModeGuardListener handles pickup/block-break the same way).
        godmodeState.setEnabled(id, true);
        player.setInvulnerable(true);

        if (settings.get(ModerationSettings.STAFFMODE_AUTO_FLY)) {
            player.setAllowFlight(true);
            player.setFlying(true);
        }
        if (settings.get(ModerationSettings.STAFFMODE_AUTO_VANISH) && !vanishService.isVanished(id)) {
            vanishService.apply(player, true);
        }
        collisionState.refresh(player, vanishService.isVanished(id), settings.get(ModerationSettings.STAFFMODE_AUTO_NOCOLLISION));
    }

    private void restoreFromSnapshot(Player player, StaffModeSnapshot snapshot) {
        UUID id = player.getUniqueId();
        state.setActive(id, false);

        ItemStack[] combined = ItemStack.deserializeItemsFromBytes(snapshot.inventoryData());
        int storageLength = combined.length - EQUIPMENT_SLOTS;
        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        inventory.setStorageContents(Arrays.copyOfRange(combined, 0, storageLength));
        inventory.setHelmet(combined[storageLength]);
        inventory.setChestplate(combined[storageLength + 1]);
        inventory.setLeggings(combined[storageLength + 2]);
        inventory.setBoots(combined[storageLength + 3]);
        inventory.setItemInOffHand(combined[storageLength + 4]);

        player.setGameMode(snapshot.gameMode());
        player.setExp(snapshot.experience());
        player.setLevel(snapshot.level());
        player.setAllowFlight(snapshot.allowFlight());
        player.setFlying(snapshot.flying());

        godmodeState.setEnabled(id, false);
        player.setInvulnerable(false);

        if (settings.get(ModerationSettings.STAFFMODE_AUTO_VANISH) && vanishService.isVanished(id)) {
            vanishService.apply(player, false);
        }
        collisionState.refresh(player, vanishService.isVanished(id), false);
    }
}

package dev.universaladmin.modules.moderation;

import dev.universaladmin.core.PlayerDirectory;
import dev.universaladmin.core.PlayerNames;
import dev.universaladmin.core.PlayerRef;
import dev.universaladmin.core.ServiceRegistry;
import dev.universaladmin.network.NetworkPlayer;
import dev.universaladmin.network.NetworkService;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * The name stored with a punishment, so the history reads "Steve", not a
 * UUID: online here, cached by this server, online elsewhere in the network,
 * or last seen in the shared player profiles. Call on the main thread.
 */
public final class TargetNames {

    private final NetworkService network;
    private final ServiceRegistry services;

    public TargetNames(NetworkService network, ServiceRegistry services) {
        this.network = network;
        this.services = services;
    }

    public CompletableFuture<String> nameOf(UUID playerId) {
        Player online = Bukkit.getPlayer(playerId);
        if (online != null) {
            return CompletableFuture.completedFuture(online.getName());
        }
        String cached = Bukkit.getOfflinePlayer(playerId).getName();
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }
        Optional<String> elsewhere = network.player(playerId).map(NetworkPlayer::name);
        if (elsewhere.isPresent()) {
            PlayerNames.remember(playerId, elsewhere.get());
            return CompletableFuture.completedFuture(elsewhere.get());
        }
        Optional<PlayerDirectory> directory = services.get(PlayerDirectory.class);
        if (directory.isEmpty()) {
            return CompletableFuture.completedFuture(fallback(playerId));
        }
        return directory.get().findById(playerId)
                .thenApply(found -> {
                    found.ifPresent(ref -> PlayerNames.remember(ref.id(), ref.name()));
                    return found.map(PlayerRef::name).orElseGet(() -> fallback(playerId));
                })
                .exceptionally(error -> fallback(playerId));
    }

    /** Never longer than a Minecraft name (the column holds 16 characters). */
    private static String fallback(UUID playerId) {
        return playerId.toString().substring(0, 8);
    }
}

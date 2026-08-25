package dev.universaladmin.modules.whitelist;

import dev.universaladmin.action.Actor;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Keeps UniversalAdmin's own {@link WhitelistEntry} metadata in sync with
 * Bukkit's native whitelist. The native whitelist ({@code
 * Bukkit.getWhitelistedPlayers()}/{@code OfflinePlayer#setWhitelisted}) is
 * always the source of truth for *membership* - this service only adds
 * metadata for whichever of those members UniversalAdmin itself added (see
 * {@link WhitelistSource}). Enabling/disabling the whitelist itself
 * ({@code Bukkit.setWhitelist}) has no metadata of its own and is called
 * directly by {@code Enable}/{@code DisableWhitelistAction} - no service
 * method needed for a single boolean flag.
 *
 * <p>{@link #listMembers()} and {@link #add}/{@link #remove} touch Bukkit
 * API and must be called from the main thread; the repository half of each
 * runs on the storage executor internally, same as any other module service.
 */
public interface WhitelistService {

    /** Every native whitelist member, each annotated with UniversalAdmin's metadata if it has any. Main thread only. */
    CompletableFuture<List<WhitelistMemberView>> listMembers();

    CompletableFuture<Optional<WhitelistEntry>> findEntry(UUID playerId);

    /** Whitelists {@code playerId} natively and records who added them (and why, and for how long). Main thread only. */
    CompletableFuture<WhitelistEntry> add(
            UUID playerId, String playerName, Actor actor, String reason, String notes, Instant expiresAt);

    /** Un-whitelists {@code playerId} natively and deletes any UniversalAdmin metadata for them, regardless of who added it. Main thread only. */
    CompletableFuture<Void> remove(UUID playerId);
}

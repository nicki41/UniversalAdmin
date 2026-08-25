package dev.universaladmin.modules.whitelist;

import dev.universaladmin.action.Actor;
import dev.universaladmin.scheduler.TaskScheduler;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

public final class DefaultWhitelistService implements WhitelistService {

    private final WhitelistEntryRepository repository;
    private final TaskScheduler scheduler;

    public DefaultWhitelistService(WhitelistEntryRepository repository, TaskScheduler scheduler) {
        this.repository = repository;
        this.scheduler = scheduler;
    }

    @Override
    public CompletableFuture<List<WhitelistMemberView>> listMembers() {
        // Every Bukkit read happens right here, synchronously, on the calling
        // (main) thread - the repository half below runs on the storage
        // executor and must never touch Bukkit itself.
        List<NativeMember> natives = Bukkit.getWhitelistedPlayers().stream()
                .map(op -> new NativeMember(op.getUniqueId(), op.getName(), op.isOnline()))
                .toList();
        return repository.findAll().thenApply(entries -> merge(natives, entries));
    }

    private List<WhitelistMemberView> merge(List<NativeMember> natives, List<WhitelistEntry> entries) {
        Map<UUID, WhitelistEntry> byId = entries.stream().collect(Collectors.toMap(WhitelistEntry::playerId, e -> e));
        return natives.stream()
                .map(n -> new WhitelistMemberView(
                        n.id(), n.name() != null ? n.name() : n.id().toString(), n.online(), Optional.ofNullable(byId.get(n.id()))))
                .sorted(Comparator.comparing(WhitelistMemberView::playerName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Override
    public CompletableFuture<Optional<WhitelistEntry>> findEntry(UUID playerId) {
        return repository.findById(playerId);
    }

    @Override
    public CompletableFuture<WhitelistEntry> add(
            UUID playerId, String playerName, Actor actor, String reason, String notes, Instant expiresAt) {
        CompletableFuture<WhitelistEntry> future = new CompletableFuture<>();
        scheduler.runOnMainThread(() -> {
            OfflinePlayer target = Bukkit.getOfflinePlayer(playerId);
            target.setWhitelisted(true);
            WhitelistEntry entry = new WhitelistEntry(playerId, playerName, WhitelistSource.UNIVERSAL_ADMIN,
                    actor.playerId(), actor.displayName(), Instant.now(), blankToNull(reason), blankToNull(notes), expiresAt);
            repository.save(entry).whenComplete((saved, error) -> {
                if (error != null) {
                    future.completeExceptionally(error);
                } else {
                    future.complete(saved);
                }
            });
        });
        return future;
    }

    @Override
    public CompletableFuture<Void> remove(UUID playerId) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        scheduler.runOnMainThread(() -> {
            OfflinePlayer target = Bukkit.getOfflinePlayer(playerId);
            target.setWhitelisted(false);
            repository.deleteById(playerId).whenComplete((ignored, error) -> {
                if (error != null) {
                    future.completeExceptionally(error);
                } else {
                    future.complete(null);
                }
            });
        });
        return future;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private record NativeMember(UUID id, String name, boolean online) {
    }
}

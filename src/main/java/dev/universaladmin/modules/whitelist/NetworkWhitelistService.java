package dev.universaladmin.modules.whitelist;

import dev.universaladmin.action.Actor;
import dev.universaladmin.network.NetworkListener;
import dev.universaladmin.network.NetworkMessage;
import dev.universaladmin.network.NetworkService;
import dev.universaladmin.network.ScopeKeys;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * The network whitelist: one list for every server of the network, kept in
 * the shared database ({@code ua_whitelist} scope {@code network},
 * {@code ua_whitelist_state}) instead of a server's native whitelist. While
 * it is on, a player gets onto any server only if they are on it (and their
 * entry has not expired) or hold
 * {@link WhitelistPermissions#BYPASS_NETWORK}; UniversalAdmin Velocity
 * checks the same at the proxy. It applies on top of each server's own
 * whitelist.
 *
 * <p>Changes are announced on {@link #TOPIC}; the other servers re-read the
 * list, and every server re-reads it every few seconds anyway. The join
 * check answers from that copy in memory.
 */
public final class NetworkWhitelistService implements WhitelistService, NetworkListener {

    /** Network channel topic: the network whitelist changed - read it again. */
    public static final String TOPIC = "whitelist.network";

    private final WhitelistEntryRepository entries;
    private final WhitelistStateRepository state;
    private final NetworkService network;
    private final Logger logger;
    /** Bumped around every write, so a refresh that overlapped one does not bring back an older read. */
    private final AtomicLong writes = new AtomicLong();

    private volatile boolean enabled;
    private volatile Map<UUID, WhitelistEntry> members = Map.of();

    /** @param entries the {@code network} scope's entries */
    public NetworkWhitelistService(
            WhitelistEntryRepository entries, WhitelistStateRepository state, NetworkService network, Logger logger) {
        this.entries = entries;
        this.state = state;
        this.network = network;
        this.logger = logger;
    }

    /** Whether there is a network whitelist here at all (network mode is on). */
    public boolean available() {
        return network.enabled();
    }

    /** Whether the network whitelist is on - from memory. */
    public boolean enabled() {
        return network.enabled() && enabled;
    }

    /** The number of players on the list, from memory. */
    public int size() {
        return members.size();
    }

    /** The names on the list, from memory - for tab completion. */
    public List<String> names() {
        return members.values().stream().map(WhitelistEntry::playerName).sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    /** Whether the network whitelist lets {@code playerId} in: it is off, or they are on it and their entry has not expired. From memory. */
    public boolean allows(UUID playerId, Instant now) {
        if (!enabled()) {
            return true;
        }
        WhitelistEntry entry = members.get(playerId);
        return entry != null && !entry.isExpired(now);
    }

    /** Reads the switch and the list again (asynchronously). */
    public CompletableFuture<Void> refresh() {
        if (!network.enabled()) {
            return CompletableFuture.completedFuture(null);
        }
        long generation = writes.get();
        return state.load(ScopeKeys.NETWORK).thenCombine(entries.findAll(), (on, all) -> {
            if (writes.get() == generation) {
                enabled = on.orElse(false);
                members = index(all);
            }
            return null;
        });
    }

    @Override
    public void onMessage(NetworkMessage message) {
        refresh().exceptionally(error -> {
            logger.log(Level.WARNING, "Failed to re-read the network whitelist after a change on " + message.source(), error);
            return null;
        });
    }

    public CompletableFuture<Void> setEnabled(boolean on, Actor actor) {
        if (!network.enabled()) {
            return CompletableFuture.failedFuture(new IllegalStateException("The network whitelist needs network mode"));
        }
        writes.incrementAndGet();
        return state.save(ScopeKeys.NETWORK, on, actor.displayName()).thenRun(() -> {
            enabled = on;
            writes.incrementAndGet();
            announce();
        });
    }

    /** Every entry, sorted by name; online means online anywhere in the network. */
    @Override
    public CompletableFuture<List<WhitelistMemberView>> listMembers() {
        return entries.findAll().thenApply(all -> all.stream()
                .map(entry -> new WhitelistMemberView(entry.playerId(), entry.playerName(),
                        network.player(entry.playerId()).isPresent(), Optional.of(entry)))
                .sorted(Comparator.comparing(WhitelistMemberView::playerName, String.CASE_INSENSITIVE_ORDER))
                .toList());
    }

    @Override
    public CompletableFuture<Optional<WhitelistEntry>> findEntry(UUID playerId) {
        return entries.findById(playerId);
    }

    @Override
    public CompletableFuture<WhitelistEntry> add(
            UUID playerId, String playerName, Actor actor, String reason, String notes, Instant expiresAt) {
        if (!network.enabled()) {
            return CompletableFuture.failedFuture(new IllegalStateException("The network whitelist needs network mode"));
        }
        WhitelistEntry entry = new WhitelistEntry(playerId, playerName, WhitelistSource.UNIVERSAL_ADMIN,
                actor.playerId(), actor.displayName(), Instant.now(), blankToNull(reason), blankToNull(notes), expiresAt);
        writes.incrementAndGet();
        return entries.save(entry).thenApply(saved -> {
            Map<UUID, WhitelistEntry> updated = new HashMap<>(members);
            updated.put(saved.playerId(), saved);
            members = Map.copyOf(updated);
            writes.incrementAndGet();
            announce();
            return saved;
        });
    }

    @Override
    public CompletableFuture<Void> remove(UUID playerId) {
        if (!network.enabled()) {
            return CompletableFuture.failedFuture(new IllegalStateException("The network whitelist needs network mode"));
        }
        writes.incrementAndGet();
        return entries.deleteById(playerId).thenRun(() -> {
            Map<UUID, WhitelistEntry> updated = new HashMap<>(members);
            updated.remove(playerId);
            members = Map.copyOf(updated);
            writes.incrementAndGet();
            announce();
        });
    }

    /**
     * Deletes expired entries - quietly: an expired entry already lets
     * nobody in, this only keeps the list short. Any server may do it.
     */
    public CompletableFuture<Integer> removeExpired() {
        if (!network.enabled()) {
            return CompletableFuture.completedFuture(0);
        }
        Instant now = Instant.now();
        return entries.findAll().thenCompose(all -> {
            List<WhitelistEntry> expired = all.stream().filter(entry -> entry.isExpired(now)).toList();
            CompletableFuture<?>[] deletions = expired.stream()
                    .map(entry -> entries.deleteById(entry.playerId()))
                    .toArray(CompletableFuture[]::new);
            return CompletableFuture.allOf(deletions).thenApply(ignored -> expired.size());
        });
    }

    private void announce() {
        network.publish(TOPIC, Map.of("changed", "true"));
    }

    private static Map<UUID, WhitelistEntry> index(List<WhitelistEntry> all) {
        return all.stream().collect(Collectors.toUnmodifiableMap(WhitelistEntry::playerId, Function.identity(), (a, b) -> a));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}

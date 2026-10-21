package dev.universaladmin.modules.moderation;

import dev.universaladmin.action.ActionTarget;
import dev.universaladmin.audit.AuditEvent;
import dev.universaladmin.audit.AuditListener;
import dev.universaladmin.status.PlayerStatus;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * {@link PlayerStatus} for displays. The toggles (vanish, staff mode, freeze,
 * godmode) come straight from the in-memory runtime states; mute, warnings
 * and the punishment count of every online player are loaded on join and
 * reloaded after every moderation action on them ({@link #onAuditEvent}) and
 * once a minute ({@link #refreshAll}, which also picks up punishments issued
 * on other servers of a network).
 */
public final class ModerationPlayerStatus implements PlayerStatus, Listener, AuditListener {

    private final PunishmentService punishments;
    private final VanishRuntimeState vanish;
    private final StaffModeState staffMode;
    private final FreezeRuntimeState freeze;
    private final GodmodeState godmode;
    private final Set<UUID> online = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Entry> entries = new ConcurrentHashMap<>();

    public ModerationPlayerStatus(PunishmentService punishments, VanishRuntimeState vanish, StaffModeState staffMode,
            FreezeRuntimeState freeze, GodmodeState godmode) {
        this.punishments = punishments;
        this.vanish = vanish;
        this.staffMode = staffMode;
        this.freeze = freeze;
        this.godmode = godmode;
    }

    @Override
    public boolean vanished(UUID playerId) {
        return vanish.isVanished(playerId);
    }

    @Override
    public boolean staffMode(UUID playerId) {
        return staffMode.isActive(playerId);
    }

    @Override
    public boolean frozen(UUID playerId) {
        return freeze.isFrozen(playerId);
    }

    @Override
    public boolean godmode(UUID playerId) {
        return godmode.isEnabled(playerId);
    }

    @Override
    public Optional<Mute> mute(UUID playerId) {
        Entry entry = entries.get(playerId);
        if (entry == null || entry.mute() == null || !entry.mute().isActiveAt(Instant.now())) {
            return Optional.empty();
        }
        return Optional.of(new Mute(entry.mute().reason(), entry.mute().expiresAt()));
    }

    @Override
    public int activeWarnings(UUID playerId) {
        Entry entry = entries.get(playerId);
        return entry == null ? 0 : entry.warnings();
    }

    @Override
    public int punishments(UUID playerId) {
        Entry entry = entries.get(playerId);
        return entry == null ? 0 : entry.total();
    }

    @Override
    public int vanishedOnline() {
        return (int) vanish.all().stream().filter(online::contains).count();
    }

    /** Starts tracking a player (also for players already online when the module enables). */
    public void track(UUID playerId) {
        online.add(playerId);
        refresh(playerId);
    }

    /** Reloads every online player's punishment values - scheduled once a minute. */
    public void refreshAll() {
        online.forEach(this::refresh);
    }

    void refresh(UUID playerId) {
        CompletableFuture<Optional<Punishment>> mute = punishments.activeMute(playerId);
        CompletableFuture<Integer> warnings = punishments.count(new PunishmentQuery(playerId, Set.of(PunishmentType.WARN), true, 1));
        CompletableFuture<Integer> total = punishments.count(new PunishmentQuery(playerId, null, null, 1));
        CompletableFuture.allOf(mute, warnings, total).thenRun(() -> {
            if (online.contains(playerId)) {
                entries.put(playerId, new Entry(mute.join().orElse(null), warnings.join(), total.join()));
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        track(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        online.remove(id);
        entries.remove(id);
    }

    @Override
    public void onAuditEvent(AuditEvent event) {
        ActionTarget target = event.target();
        if (!event.success() || target == null || !ActionTarget.PLAYER_TYPE.equals(target.type())
                || !ModerationModule.ID.key().name().equals(event.module())) {
            return;
        }
        try {
            UUID id = UUID.fromString(target.id());
            if (online.contains(id)) {
                refresh(id);
            }
        } catch (IllegalArgumentException e) {
            // Not a player id - nothing to refresh.
        }
    }

    private record Entry(Punishment mute, int warnings, int total) {
    }
}

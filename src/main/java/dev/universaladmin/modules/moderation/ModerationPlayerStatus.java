package dev.universaladmin.modules.moderation;

import dev.universaladmin.action.ActionTarget;
import dev.universaladmin.audit.AuditEvent;
import dev.universaladmin.audit.AuditListener;
import dev.universaladmin.status.MuteKind;
import dev.universaladmin.status.PlayerStatus;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * {@link PlayerStatus} for displays and for the voice chat extension. The
 * toggles (vanish, staff mode, freeze, godmode) come straight from the
 * in-memory runtime states; mutes, warnings and the punishment count of
 * every online player are loaded on join and reloaded after every
 * moderation action on them ({@link #onAuditEvent}), when another server of
 * a network reports one ({@link #refreshIfOnline}) and once a minute
 * ({@link #refreshAll}). Reads come from immutable snapshots in a concurrent
 * map, so any thread may ask - the voice chat asks from its own.
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
        return mute(playerId, kind -> true);
    }

    @Override
    public Optional<Mute> chatMute(UUID playerId) {
        return mute(playerId, MuteKind::coversChat);
    }

    @Override
    public Optional<Mute> voiceMute(UUID playerId) {
        return mute(playerId, MuteKind::coversVoice);
    }

    private Optional<Mute> mute(UUID playerId, Predicate<MuteKind> covers) {
        Entry entry = entries.get(playerId);
        if (entry == null) {
            return Optional.empty();
        }
        Instant now = Instant.now();
        List<Punishment> inForce = entry.mutes().stream().filter(mute -> mute.isActiveAt(now)).toList();
        return PunishmentService.covering(inForce, covers)
                .map(mute -> new Mute(mute.reason(), mute.expiresAt(), mute.muteKind()));
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

    /** Reloads {@code playerId}'s values if they are online here - for punishments another server of the network reports. */
    public void refreshIfOnline(UUID playerId) {
        if (online.contains(playerId)) {
            refresh(playerId);
        }
    }

    void refresh(UUID playerId) {
        CompletableFuture<List<Punishment>> mutes = punishments.activeMutes(playerId);
        CompletableFuture<Integer> warnings = punishments.count(new PunishmentQuery(playerId, Set.of(PunishmentType.WARN), true, 1));
        CompletableFuture<Integer> total = punishments.count(new PunishmentQuery(playerId, null, null, 1));
        CompletableFuture.allOf(mutes, warnings, total).thenRun(() -> {
            if (online.contains(playerId)) {
                entries.put(playerId, new Entry(mutes.join(), warnings.join(), total.join()));
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

    /** @param mutes every mute in force at load time, newest first - read on any thread, never changed */
    private record Entry(List<Punishment> mutes, int warnings, int total) {

        Entry {
            mutes = List.copyOf(mutes);
        }
    }
}

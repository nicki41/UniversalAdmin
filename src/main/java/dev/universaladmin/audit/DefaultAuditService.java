package dev.universaladmin.audit;

import dev.universaladmin.action.ActionTarget;
import dev.universaladmin.action.Actor;
import dev.universaladmin.action.ActorType;
import dev.universaladmin.core.PlayerNames;
import dev.universaladmin.settings.CoreSettings;
import dev.universaladmin.settings.SettingsService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class DefaultAuditService implements AuditService {

    private static final Logger LOGGER = Logger.getLogger(DefaultAuditService.class.getName());

    private final AuditEventRepository repository;
    private final SettingsService settings;
    private final Supplier<String> serverName;
    private final List<AuditListener> listeners = new CopyOnWriteArrayList<>();

    public DefaultAuditService(AuditEventRepository repository, SettingsService settings) {
        this(repository, settings, () -> null);
    }

    /**
     * @param serverName in network mode the name of this server, stored with
     *                   every entry as {@code server} metadata (the database -
     *                   and with it the audit log - is shared by all servers);
     *                   {@code null} otherwise
     */
    public DefaultAuditService(AuditEventRepository repository, SettingsService settings, Supplier<String> serverName) {
        this.repository = repository;
        this.settings = settings;
        this.serverName = serverName;
    }

    @Override
    public CompletableFuture<Void> record(AuditEvent entry) {
        if (!settings.get(CoreSettings.AUDIT_ENABLED)) {
            return CompletableFuture.completedFuture(null);
        }
        return repository.save(withServer(withRealNames(entry))).thenAccept(this::publish);
    }

    /**
     * The audit log names nicked players by their real name - whoever built
     * the entry may only have known the nick. The nick goes into the
     * metadata ({@code actor-nick}, {@code target-nick}).
     */
    private AuditEvent withRealNames(AuditEvent entry) {
        Actor actor = entry.actor();
        ActionTarget target = entry.target();
        Map<String, Object> metadata = new HashMap<>(entry.metadata());
        if (actor.type() == ActorType.PLAYER && actor.playerId() != null) {
            String real = PlayerNames.real(actor.playerId(), actor.displayName());
            if (!real.equals(actor.displayName())) {
                metadata.put("actor-nick", actor.displayName());
                actor = new Actor(actor.type(), actor.playerId(), real, actor.permissions());
            }
        }
        if (target != null && ActionTarget.PLAYER_TYPE.equals(target.type()) && target.displayName() != null) {
            String real = PlayerNames.real(playerId(target.id()), target.displayName());
            if (!real.equals(target.displayName())) {
                metadata.put("target-nick", target.displayName());
                target = new ActionTarget(target.type(), target.id(), real);
            }
        }
        if (actor == entry.actor() && target == entry.target()) {
            return entry;
        }
        return new AuditEvent(entry.id(), entry.timestamp(), actor, entry.type(), entry.module(), target,
                entry.source(), entry.success(), entry.reason(), entry.oldValue(), entry.newValue(), entry.world(),
                entry.position(), entry.summary(), metadata, entry.correlationId());
    }

    private static UUID playerId(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private AuditEvent withServer(AuditEvent entry) {
        String server = serverName.get();
        if (server == null || entry.metadata().containsKey("server")) {
            return entry;
        }
        Map<String, Object> metadata = new HashMap<>(entry.metadata());
        metadata.put("server", server);
        return new AuditEvent(entry.id(), entry.timestamp(), entry.actor(), entry.type(), entry.module(), entry.target(),
                entry.source(), entry.success(), entry.reason(), entry.oldValue(), entry.newValue(), entry.world(),
                entry.position(), entry.summary(), metadata, entry.correlationId());
    }

    @Override
    public void subscribe(AuditListener listener) {
        listeners.add(listener);
    }

    @Override
    public void unsubscribe(AuditListener listener) {
        listeners.remove(listener);
    }

    private void publish(AuditEvent stored) {
        for (AuditListener listener : listeners) {
            try {
                listener.onAuditEvent(stored);
            } catch (RuntimeException e) {
                LOGGER.log(Level.WARNING, "Audit listener " + listener.getClass().getName() + " threw", e);
            }
        }
    }

    @Override
    public CompletableFuture<AuditPage> query(AuditQuery query) {
        return repository.query(query);
    }

    @Override
    public CompletableFuture<List<AuditEvent>> recent(int limit) {
        return repository.recent(limit);
    }

    @Override
    public CompletableFuture<Optional<AuditEvent>> findById(Long id) {
        return repository.findById(id);
    }

    @Override
    public CompletableFuture<Integer> cleanupExpired() {
        int retentionDays = settings.get(CoreSettings.AUDIT_RETENTION_DAYS);
        if (retentionDays <= 0) {
            // 0 = unlimited retention, see CoreSettings#AUDIT_RETENTION_DAYS.
            return CompletableFuture.completedFuture(0);
        }
        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        return repository.deleteOlderThan(cutoff);
    }
}

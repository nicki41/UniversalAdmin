package dev.universaladmin.audit;

import dev.universaladmin.settings.CoreSettings;
import dev.universaladmin.settings.SettingsService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class DefaultAuditService implements AuditService {

    private final AuditEventRepository repository;
    private final SettingsService settings;

    public DefaultAuditService(AuditEventRepository repository, SettingsService settings) {
        this.repository = repository;
        this.settings = settings;
    }

    @Override
    public CompletableFuture<Void> record(AuditEvent entry) {
        if (!settings.get(CoreSettings.AUDIT_ENABLED)) {
            return CompletableFuture.completedFuture(null);
        }
        return repository.save(entry).thenApply(event -> null);
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

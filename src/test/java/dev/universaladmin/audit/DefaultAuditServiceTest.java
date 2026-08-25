package dev.universaladmin.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.universaladmin.action.Actor;
import dev.universaladmin.action.Source;
import dev.universaladmin.settings.CoreSettings;
import dev.universaladmin.settings.SettingsService;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

/**
 * Exercises {@link DefaultAuditService} against mocked {@link AuditEventRepository}/
 * {@link SettingsService} - the retention/enabled *policy*, not real SQL
 * (that's {@code audit.jdbc.JdbcAuditEventRepositoryTest}).
 */
class DefaultAuditServiceTest {

    @Test
    void recordSkipsPersistingWhenAuditingIsDisabled() {
        AuditEventRepository repository = mock(AuditEventRepository.class);
        SettingsService settings = mock(SettingsService.class);
        when(settings.get(CoreSettings.AUDIT_ENABLED)).thenReturn(false);
        DefaultAuditService service = new DefaultAuditService(repository, settings);

        service.record(entry()).join();

        verify(repository, never()).save(any());
    }

    @Test
    void recordPersistsWhenAuditingIsEnabled() {
        AuditEventRepository repository = mock(AuditEventRepository.class);
        SettingsService settings = mock(SettingsService.class);
        when(settings.get(CoreSettings.AUDIT_ENABLED)).thenReturn(true);
        AuditEvent entry = entry();
        when(repository.save(entry)).thenReturn(CompletableFuture.completedFuture(entry));
        DefaultAuditService service = new DefaultAuditService(repository, settings);

        service.record(entry).join();

        verify(repository).save(entry);
    }

    @Test
    void cleanupExpiredIsANoOpWhenRetentionIsUnlimited() {
        AuditEventRepository repository = mock(AuditEventRepository.class);
        SettingsService settings = mock(SettingsService.class);
        when(settings.get(CoreSettings.AUDIT_RETENTION_DAYS)).thenReturn(0);
        DefaultAuditService service = new DefaultAuditService(repository, settings);

        int deleted = service.cleanupExpired().join();

        assertEquals(0, deleted);
        verify(repository, never()).deleteOlderThan(any());
    }

    @Test
    void cleanupExpiredDeletesOlderThanTheConfiguredRetentionWindow() {
        AuditEventRepository repository = mock(AuditEventRepository.class);
        SettingsService settings = mock(SettingsService.class);
        when(settings.get(CoreSettings.AUDIT_RETENTION_DAYS)).thenReturn(30);
        when(repository.deleteOlderThan(any())).thenReturn(CompletableFuture.completedFuture(5));
        DefaultAuditService service = new DefaultAuditService(repository, settings);

        int deleted = service.cleanupExpired().join();

        assertEquals(5, deleted);
        verify(repository).deleteOlderThan(any(Instant.class));
    }

    private AuditEvent entry() {
        return AuditEvent.builder(AuditEventType.core("config.reload"), Actor.console(), Source.COMMAND, "test").build();
    }
}

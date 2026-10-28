package dev.universaladmin.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.universaladmin.action.ActionTarget;
import dev.universaladmin.action.Actor;
import dev.universaladmin.action.Source;
import dev.universaladmin.core.ServiceRegistry;
import dev.universaladmin.core.UniversalAdmin;
import dev.universaladmin.core.UniversalAdminProvider;
import dev.universaladmin.permission.PermissionEvaluator;
import dev.universaladmin.settings.CoreSettings;
import dev.universaladmin.settings.SettingsService;
import dev.universaladmin.status.Nicks;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

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

    @Test
    void nickedPlayersAreRecordedUnderTheirRealName() {
        UUID admin = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        Nicks nicks = mock(Nicks.class);
        when(nicks.nick(admin)).thenReturn(Optional.of(new Nicks.Nick(admin, "Notch", "Admin", null, null)));
        when(nicks.nick(target)).thenReturn(Optional.empty());
        ServiceRegistry services = new ServiceRegistry();
        services.register(Nicks.class, nicks);
        UniversalAdmin platform = mock(UniversalAdmin.class);
        when(platform.services()).thenReturn(services);
        AuditEventRepository repository = mock(AuditEventRepository.class);
        when(repository.save(any())).thenAnswer(invocation -> CompletableFuture.completedFuture(invocation.getArgument(0)));
        SettingsService settings = mock(SettingsService.class);
        when(settings.get(CoreSettings.AUDIT_ENABLED)).thenReturn(true);
        DefaultAuditService service = new DefaultAuditService(repository, settings);
        AuditEvent entry = AuditEvent.builder(AuditEventType.core("players.heal"),
                        Actor.player(admin, "Notch", PermissionEvaluator.allowAll()), Source.COMMAND, "Healed")
                .target(ActionTarget.player(admin, "Notch"))
                .build();
        AuditEvent other = AuditEvent.builder(AuditEventType.core("players.heal"),
                        Actor.player(admin, "Notch", PermissionEvaluator.allowAll()), Source.COMMAND, "Healed")
                .target(ActionTarget.player(target, "Steve"))
                .build();

        UniversalAdminProvider.register(platform);
        try {
            service.record(entry).join();
            service.record(other).join();
        } finally {
            UniversalAdminProvider.unregister(platform);
        }

        ArgumentCaptor<AuditEvent> saved = ArgumentCaptor.forClass(AuditEvent.class);
        verify(repository, times(2)).save(saved.capture());
        AuditEvent first = saved.getAllValues().get(0);
        assertEquals("Admin", first.actor().displayName());
        assertEquals("Admin", first.target().displayName());
        assertEquals("Notch", first.metadata().get("actor-nick"));
        assertEquals("Notch", first.metadata().get("target-nick"));
        AuditEvent second = saved.getAllValues().get(1);
        assertEquals("Steve", second.target().displayName());
        assertFalse(second.metadata().containsKey("target-nick"));
    }

    private AuditEvent entry() {
        return AuditEvent.builder(AuditEventType.core("config.reload"), Actor.console(), Source.COMMAND, "test").build();
    }
}

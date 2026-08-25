package dev.universaladmin.modules.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.universaladmin.action.Actor;
import dev.universaladmin.scheduler.TaskScheduler;
import dev.universaladmin.settings.CoreSettings;
import dev.universaladmin.settings.SettingKey;
import dev.universaladmin.settings.SettingsService;
import dev.universaladmin.settings.SettingValue;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.logging.Logger;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

/**
 * Exercises {@link DefaultMaintenanceService} against an in-memory {@link
 * MaintenanceStateRepository} fake - same pattern as {@code PlayerServiceTest}.
 * {@link MaintenanceService#current()}'s "kick non-bypass players" branch of
 * {@link MaintenanceService#enable} isn't covered - it reaches into live
 * Bukkit state ({@code Bukkit.getOnlinePlayers()}), which needs a running or
 * mocked server this project's unit tests deliberately don't stand up (same
 * carve-out {@code PlayerServiceTest} takes for {@code PlayerService#snapshot}).
 */
class DefaultMaintenanceServiceTest {

    private static final TaskScheduler POISON = new TaskScheduler() {
        @Override
        public <T> CompletableFuture<T> supplyAsync(Supplier<T> task) {
            throw new UnsupportedOperationException("not expected in this test");
        }

        @Override
        public CompletableFuture<Void> runAsync(Runnable task) {
            throw new UnsupportedOperationException("not expected in this test");
        }

        @Override
        public void runOnMainThread(Runnable task) {
            throw new UnsupportedOperationException("not expected in this test - would touch live Bukkit state");
        }

        @Override
        public void close() {
        }
    };

    private final Actor actor = Actor.system("test");

    @Test
    void seedsFromCoreSettingsWhileTheRepositoryLoadIsPending() {
        CompletableFuture<Optional<MaintenanceState>> pendingLoad = new CompletableFuture<>();
        DefaultMaintenanceService service = new DefaultMaintenanceService(
                new FakeRepository(pendingLoad), fakeSettings(true, "<red>Maintenance!"), POISON, Logger.getAnonymousLogger());

        assertTrue(service.current().enabled());
        assertEquals("<red>Maintenance!", service.effectiveKickMessage());

        pendingLoad.complete(Optional.of(MaintenanceState.disabled()));
        assertFalse(service.current().enabled());
    }

    @Test
    void enablePersistsAndUpdatesCachedState() {
        FakeRepository repository = new FakeRepository(CompletableFuture.completedFuture(Optional.empty()));
        DefaultMaintenanceService service =
                new DefaultMaintenanceService(repository, fakeSettings(false, "default"), POISON, Logger.getAnonymousLogger());

        MaintenanceState result = service.enable("scheduled downtime", null, false, actor).join();

        assertTrue(result.enabled());
        assertEquals("scheduled downtime", result.reason());
        assertEquals(result, service.current());
        assertEquals(result, repository.saved);
    }

    @Test
    void disablePreservesReasonAndMessageButClearsEnabled() {
        FakeRepository repository = new FakeRepository(CompletableFuture.completedFuture(Optional.empty()));
        DefaultMaintenanceService service =
                new DefaultMaintenanceService(repository, fakeSettings(false, "default"), POISON, Logger.getAnonymousLogger());
        service.enable("reason", "custom message", false, actor).join();

        MaintenanceState result = service.disable(actor).join();

        assertFalse(result.enabled());
        assertEquals("reason", result.reason());
        assertEquals("custom message", result.message());
    }

    @Test
    void setAllowedPlayersNormalizesNamesToLowercase() {
        FakeRepository repository = new FakeRepository(CompletableFuture.completedFuture(Optional.empty()));
        DefaultMaintenanceService service =
                new DefaultMaintenanceService(repository, fakeSettings(false, "default"), POISON, Logger.getAnonymousLogger());

        MaintenanceState result = service.setAllowedPlayers(Set.of("Notch", "JEB_"), actor).join();

        assertEquals(Set.of("notch", "jeb_"), result.allowedPlayers());
    }

    @Test
    void isAllowedIsAlwaysTrueWhenMaintenanceIsDisabled() {
        FakeRepository repository = new FakeRepository(CompletableFuture.completedFuture(Optional.of(MaintenanceState.disabled())));
        DefaultMaintenanceService service =
                new DefaultMaintenanceService(repository, fakeSettings(false, "default"), POISON, Logger.getAnonymousLogger());
        Player player = mock(Player.class);
        when(player.hasPermission(ServerPermissions.BYPASS_MAINTENANCE.value())).thenReturn(false);
        when(player.getName()).thenReturn("Steve");

        assertTrue(service.isAllowed(player));
    }

    @Test
    void isAllowedChecksBypassPermissionAndAllowList() {
        FakeRepository repository = new FakeRepository(CompletableFuture.completedFuture(Optional.empty()));
        DefaultMaintenanceService service =
                new DefaultMaintenanceService(repository, fakeSettings(false, "default"), POISON, Logger.getAnonymousLogger());
        service.enable("reason", null, false, actor).join();
        service.setAllowedPlayers(Set.of("Alice"), actor).join();

        Player bypassPlayer = mock(Player.class);
        when(bypassPlayer.hasPermission(ServerPermissions.BYPASS_MAINTENANCE.value())).thenReturn(true);
        when(bypassPlayer.getName()).thenReturn("Steve");
        assertTrue(service.isAllowed(bypassPlayer));

        Player allowListedPlayer = mock(Player.class);
        when(allowListedPlayer.hasPermission(ServerPermissions.BYPASS_MAINTENANCE.value())).thenReturn(false);
        when(allowListedPlayer.getName()).thenReturn("alice");
        assertTrue(service.isAllowed(allowListedPlayer));

        Player blockedPlayer = mock(Player.class);
        when(blockedPlayer.hasPermission(ServerPermissions.BYPASS_MAINTENANCE.value())).thenReturn(false);
        when(blockedPlayer.getName()).thenReturn("Bob");
        assertFalse(service.isAllowed(blockedPlayer));
    }

    private SettingsService fakeSettings(boolean maintenanceEnabled, String kickMessage) {
        Map<SettingKey<?>, Object> values = new HashMap<>();
        values.put(CoreSettings.MAINTENANCE_ENABLED, maintenanceEnabled);
        values.put(CoreSettings.MAINTENANCE_KICK_MESSAGE, kickMessage);
        return new FakeSettingsService(values);
    }

    private record FakeSettingsService(Map<SettingKey<?>, Object> values) implements SettingsService {
        @Override
        @SuppressWarnings("unchecked")
        public <T> T get(SettingKey<T> key) {
            return (T) values.get(key);
        }

        @Override
        public <T> SettingValue<T> getValue(SettingKey<T> key) {
            throw new UnsupportedOperationException("not needed for this test");
        }

        @Override
        public dev.universaladmin.settings.ConfigReloadResult reload() {
            throw new UnsupportedOperationException("not needed for this test");
        }
    }

    private static final class FakeRepository implements MaintenanceStateRepository {
        private final CompletableFuture<Optional<MaintenanceState>> loadResult;
        private volatile MaintenanceState saved;

        private FakeRepository(CompletableFuture<Optional<MaintenanceState>> loadResult) {
            this.loadResult = loadResult;
        }

        @Override
        public CompletableFuture<Optional<MaintenanceState>> load() {
            return loadResult;
        }

        @Override
        public CompletableFuture<Void> save(MaintenanceState state) {
            this.saved = state;
            return CompletableFuture.completedFuture(null);
        }
    }
}

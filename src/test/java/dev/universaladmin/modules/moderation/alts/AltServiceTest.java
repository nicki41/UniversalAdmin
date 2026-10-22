package dev.universaladmin.modules.moderation.alts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.universaladmin.modules.moderation.ModerationSettings;
import dev.universaladmin.modules.moderation.Punishment;
import dev.universaladmin.modules.moderation.PunishmentRepository;
import dev.universaladmin.modules.moderation.PunishmentService;
import dev.universaladmin.modules.moderation.PunishmentType;
import dev.universaladmin.modules.moderation.jdbc.JdbcAltRepository;
import dev.universaladmin.scheduler.TaskScheduler;
import dev.universaladmin.settings.ConfigReloadResult;
import dev.universaladmin.settings.SettingKey;
import dev.universaladmin.settings.SettingValue;
import dev.universaladmin.settings.SettingsService;
import dev.universaladmin.storage.DatabaseConfig;
import dev.universaladmin.storage.MigrationRunner;
import dev.universaladmin.storage.jdbc.DataSourceFactory;
import java.net.InetAddress;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.logging.Logger;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Alt detection against a real SQLite database ({@link AltsMigration},
 * {@link JdbcAltRepository}) with a fake punishment store: what is stored,
 * what counts as an alt, and that nothing is stored while it is off.
 */
class AltServiceTest {

    private static final TaskScheduler DIRECT = new TaskScheduler() {
        @Override
        public <T> CompletableFuture<T> supplyAsync(Supplier<T> task) {
            return CompletableFuture.completedFuture(task.get());
        }

        @Override
        public CompletableFuture<Void> runAsync(Runnable task) {
            task.run();
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void runOnMainThread(Runnable task) {
            task.run();
        }

        @Override
        public void close() {
        }
    };

    private static final UUID STEVE = UUID.randomUUID();
    private static final UUID STEVES_ALT = UUID.randomUUID();
    private static final UUID SISTER = UUID.randomUUID();

    @TempDir
    Path dir;

    private DataSource dataSource;
    private JdbcAltRepository repository;
    private final Map<SettingKey<?>, Object> values = new HashMap<>();
    private AltService service;

    @BeforeEach
    void setUp() {
        dataSource = DataSourceFactory.create(DatabaseConfig.sqlite("alts.db"), dir);
        MigrationRunner runner = new MigrationRunner(dataSource, Logger.getLogger("test"));
        runner.register(new AltsMigration());
        runner.runPending();
        repository = new JdbcAltRepository(dataSource, DIRECT);
        values.put(ModerationSettings.ALTS_ENABLED, true);
        values.put(ModerationSettings.ALTS_RETENTION, Duration.ofDays(90));
        PunishmentRepository punishments = mock(PunishmentRepository.class);
        when(punishments.findActiveBan(any(UUID.class), any(Instant.class))).thenAnswer(invocation -> CompletableFuture.completedFuture(
                STEVE.equals(invocation.getArgument(0))
                        ? Optional.of(Punishment.issue(PunishmentType.BAN, STEVE, "Steve", null, null, "Mod", "cheating", null))
                        : Optional.empty()));
        service = new AltService(repository, new PunishmentService(punishments), new MapSettings(values));
    }

    @AfterEach
    void tearDown() throws Exception {
        ((AutoCloseable) dataSource).close();
    }

    @Test
    void aNewAccountFromABannedAccountsAddressIsReported() throws Exception {
        InetAddress home = InetAddress.getByName("198.51.100.20");
        assertTrue(service.checkLogin(STEVE, "Steve", home).join().isEmpty());

        List<AltMatch> banned = service.checkLogin(STEVES_ALT, "SteveAlt", home).join();

        assertEquals(1, banned.size());
        assertEquals("Steve", banned.get(0).name());
        assertTrue(banned.get(0).banned());
    }

    @Test
    void altsListEveryAccountThatSharedAnAddressWithBanStatus() throws Exception {
        InetAddress home = InetAddress.getByName("198.51.100.20");
        InetAddress school = InetAddress.getByName("198.51.100.99");
        service.checkLogin(STEVE, "Steve", home).join();
        service.checkLogin(STEVE, "Steve", school).join();
        service.checkLogin(SISTER, "Sister", home).join();
        service.checkLogin(STEVES_ALT, "SteveAlt", home).join();
        service.checkLogin(STEVES_ALT, "SteveAlt", school).join();
        service.checkLogin(UUID.randomUUID(), "Stranger", InetAddress.getByName("192.0.2.1")).join();

        List<AltMatch> alts = service.alts(STEVES_ALT).join();

        assertEquals(List.of("Steve", "Sister"), alts.stream().map(AltMatch::name).sorted((a, b) -> b.compareTo(a)).toList());
        AltMatch steve = alts.stream().filter(alt -> alt.name().equals("Steve")).findFirst().orElseThrow();
        assertEquals(2, steve.sharedAddresses());
        assertTrue(steve.banned());
    }

    @Test
    void nothingIsStoredWhileItIsOff() throws Exception {
        values.put(ModerationSettings.ALTS_ENABLED, false);
        InetAddress home = InetAddress.getByName("198.51.100.20");
        service.checkLogin(STEVE, "Steve", home).join();
        values.put(ModerationSettings.ALTS_ENABLED, true);

        assertTrue(service.checkLogin(STEVES_ALT, "SteveAlt", home).join().isEmpty());
    }

    @Test
    void forgettingAndTheRetentionPeriodDeleteAddresses() throws Exception {
        InetAddress home = InetAddress.getByName("198.51.100.20");
        service.checkLogin(STEVE, "Steve", home).join();
        service.checkLogin(SISTER, "Sister", home).join();
        repository.record(STEVES_ALT, "SteveAlt", "old-hash", Instant.now().minus(Duration.ofDays(100))).join();

        assertEquals(1, service.sweep().join());
        assertEquals(1, service.forget(STEVE).join());
        assertTrue(service.alts(SISTER).join().isEmpty());
    }

    @Test
    void theKeyIsCreatedOnceAndKept() {
        String first = repository.secret().join();

        assertEquals(first, repository.secret().join());
        assertEquals(64, first.length());
    }

    private record MapSettings(Map<SettingKey<?>, Object> values) implements SettingsService {
        @Override
        @SuppressWarnings("unchecked")
        public <T> T get(SettingKey<T> key) {
            return (T) values.get(key);
        }

        @Override
        public <T> SettingValue<T> getValue(SettingKey<T> key) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ConfigReloadResult reload() {
            throw new UnsupportedOperationException();
        }
    }
}

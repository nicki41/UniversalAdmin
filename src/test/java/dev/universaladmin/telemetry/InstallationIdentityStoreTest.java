package dev.universaladmin.telemetry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Persistence of the installation id against a real (temporary) data folder -
 * the same "use the real thing where a fake would prove less" approach the
 * migration tests take (see docs/development/testing.md).
 */
class InstallationIdentityStoreTest {

    private static final Logger LOGGER = Logger.getLogger("telemetry-test");

    @Test
    void generatesAndPersistsAnIdentityOnFirstUse(@TempDir Path dataFolder) throws IOException {
        InstallationIdentity created = new InstallationIdentityStore(dataFolder, LOGGER).loadOrCreate();

        Path file = dataFolder.resolve(InstallationIdentityStore.FILE_NAME);
        assertTrue(Files.isRegularFile(file), "the id file should have been written");
        assertTrue(Files.readString(file, StandardCharsets.UTF_8).contains(created.value()));
    }

    @Test
    void returnsTheSameIdentityOnEveryLaterStart(@TempDir Path dataFolder) {
        InstallationIdentity first = new InstallationIdentityStore(dataFolder, LOGGER).loadOrCreate();
        InstallationIdentity second = new InstallationIdentityStore(dataFolder, LOGGER).loadOrCreate();
        InstallationIdentity third = new InstallationIdentityStore(dataFolder, LOGGER).loadOrCreate();

        assertEquals(first, second);
        assertEquals(first, third);
    }

    @Test
    void createsTheDataFolderIfItDoesNotExistYet(@TempDir Path parent) {
        Path dataFolder = parent.resolve("UniversalAdmin");

        InstallationIdentity identity = new InstallationIdentityStore(dataFolder, LOGGER).loadOrCreate();

        assertTrue(Files.isRegularFile(dataFolder.resolve(InstallationIdentityStore.FILE_NAME)));
        assertEquals(32, identity.value().length());
    }

    @Test
    void replacesAnUnreadableOrInvalidStoredValueInsteadOfFailing(@TempDir Path dataFolder) throws IOException {
        Path file = dataFolder.resolve(InstallationIdentityStore.FILE_NAME);
        Files.writeString(file, InstallationIdentityStore.KEY + ": \"nonsense\"\n", StandardCharsets.UTF_8);

        InstallationIdentity identity = new InstallationIdentityStore(dataFolder, LOGGER).loadOrCreate();

        assertTrue(identity.value().matches("[0-9a-f]{32}"));
        assertTrue(Files.readString(file, StandardCharsets.UTF_8).contains(identity.value()));
    }

    @Test
    void twoSeparateInstallationsGetSeparateIdentities(@TempDir Path parent) {
        InstallationIdentity one = new InstallationIdentityStore(parent.resolve("server-one"), LOGGER).loadOrCreate();
        InstallationIdentity two = new InstallationIdentityStore(parent.resolve("server-two"), LOGGER).loadOrCreate();

        assertNotEquals(one, two);
    }
}

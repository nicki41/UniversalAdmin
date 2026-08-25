package dev.universaladmin.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StorageServiceTest {

    @Test
    void sqliteInitializesAndTracksHealthThroughItsLifecycle(@TempDir Path dir) {
        StorageService storage = new StorageService(DatabaseConfig.sqlite("test.db"), dir, Logger.getLogger("test"));
        try {
            assertEquals(DatabaseHealth.READY, storage.health());
            assertTrue(Files.exists(dir.resolve("test.db")));
        } finally {
            storage.close();
        }
        assertEquals(DatabaseHealth.DISCONNECTED, storage.health());
    }

    @Test
    void invalidSqlitePathFailsConstructionInsteadOfStartingDegraded(@TempDir Path dir) throws Exception {
        // A directory can't be opened as a SQLite database file - this fails
        // fast during pool validation, unlike an unreachable MySQL host,
        // which would need a network timeout to reproduce here. Either way,
        // StorageService is a critical bootstrap component: it must throw
        // rather than return an object that silently can't persist anything.
        // See the "Health" section on StorageService's class Javadoc.
        Path directoryAsDbFile = dir.resolve("not-a-file");
        Files.createDirectory(directoryAsDbFile);

        assertThrows(RuntimeException.class, () -> new StorageService(
                DatabaseConfig.sqlite(directoryAsDbFile.getFileName().toString()), dir, Logger.getLogger("test")));
    }
}

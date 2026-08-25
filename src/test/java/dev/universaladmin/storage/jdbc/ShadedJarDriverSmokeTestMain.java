package dev.universaladmin.storage.jdbc;

import dev.universaladmin.storage.DatabaseConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import javax.sql.DataSource;

/**
 * Not a JUnit test - a standalone {@code main} run by the Gradle
 * {@code verifyShadedJarDrivers} task (see {@code build.gradle.kts})
 * directly against the built {@code shadowJar}, nothing else on the
 * classpath. Exists because a real, severe bug in the shaded jar's driver
 * bundling was invisible to every other check in this project: the
 * ordinary test suite runs against the unshaded, unminimized dependency
 * jars on the test classpath, so it can't catch a driver class that
 * {@code minimize()} silently stripped, or a JNI-native driver broken by
 * relocation - both only fail once a real server tries to open a real
 * connection through the actual, final jar. This does exactly that: opens
 * a real SQLite connection through {@link DataSourceFactory}, using only
 * classes present in the shaded jar.
 *
 * <p>Only SQLite is opened end-to-end here (no MySQL/MariaDB server is
 * available in CI); the MariaDB driver class is still confirmed loadable
 * via {@code Class.forName}, which is enough to catch the two failure
 * modes above (missing class, broken relocation) even without a live
 * server to connect to.
 */
public final class ShadedJarDriverSmokeTestMain {

    private ShadedJarDriverSmokeTestMain() {
    }

    public static void main(String[] args) throws Exception {
        // This class is test-only source, never itself passed through
        // ShadowJar's relocation - so unlike DataSourceFactory (whose
        // "org.mariadb.jdbc.Driver" string literal gets rewritten by
        // Shadow because that class ships inside the shaded jar), the
        // literal here must already name the *relocated* class as it
        // actually exists on this task's classpath.
        Class.forName("dev.universaladmin.libs.mariadb.jdbc.Driver");

        Path tempDir = Files.createTempDirectory("ua-shaded-jar-driver-check");
        DataSource dataSource = DataSourceFactory.create(DatabaseConfig.sqlite("smoke.db"), tempDir);
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE smoke_test (id INTEGER)");
        } finally {
            ((AutoCloseable) dataSource).close();
        }
        System.out.println("Shaded jar driver smoke test passed: SQLite connects, MariaDB driver class loads.");
    }
}

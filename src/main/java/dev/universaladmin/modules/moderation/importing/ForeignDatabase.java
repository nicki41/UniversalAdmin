package dev.universaladmin.modules.moderation.importing;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

/**
 * Opens the database of another plugin, read-only and outside
 * UniversalAdmin's own connection pool.
 *
 * <ul>
 *   <li>MySQL/MariaDB and SQLite use the drivers UniversalAdmin ships.</li>
 *   <li>Embedded H2 (LiteBans) and HSQLDB (AdvancedBan) files can only be
 *       read by the driver version that wrote them, so the driver is taken
 *       from the source plugin itself: its jar, its own folder, or the
 *       server's {@code libraries} folder where Paper puts downloaded plugin
 *       libraries - found by class file name, so relocated copies work too.
 *       See ADR 0015.</li>
 * </ul>
 */
final class ForeignDatabase {

    private static final int MAX_DEPTH = 6;

    private ForeignDatabase() {
    }

    /** A MySQL or MariaDB database, through the MariaDB driver UniversalAdmin ships. */
    static Connection mysql(String source, String host, int port, String database, String user, String password)
            throws ImportException {
        Properties properties = new Properties();
        properties.setProperty("user", user == null ? "" : user);
        properties.setProperty("password", password == null ? "" : password);
        properties.setProperty("connectTimeout", "10000");
        String url = "jdbc:mariadb://" + host + ":" + port + "/" + database;
        try {
            Driver driver = (Driver) Class.forName("org.mariadb.jdbc.Driver").getDeclaredConstructor().newInstance();
            Connection connection = driver.connect(url, properties);
            if (connection == null) {
                throw new ImportException("moderation.import.error.connect", source, url);
            }
            connection.setReadOnly(true);
            return connection;
        } catch (SQLException e) {
            throw new ImportException("moderation.import.error.connect", e, source, e.getMessage());
        } catch (ReflectiveOperationException e) {
            throw new ImportException("moderation.import.error.driver", e, source, "MariaDB");
        }
    }

    /** A SQLite file, through the SQLite driver UniversalAdmin ships. */
    static Connection sqlite(String source, Path file) throws ImportException {
        try {
            Driver driver = (Driver) Class.forName("org.sqlite.JDBC").getDeclaredConstructor().newInstance();
            Properties properties = new Properties();
            properties.setProperty("open_mode", "1"); // SQLITE_OPEN_READONLY
            return driver.connect("jdbc:sqlite:" + file.toAbsolutePath(), properties);
        } catch (SQLException e) {
            throw new ImportException("moderation.import.error.connect", e, source, e.getMessage());
        } catch (ReflectiveOperationException e) {
            throw new ImportException("moderation.import.error.driver", e, source, "SQLite");
        }
    }

    /**
     * Opens {@code url} with a driver class found in {@code searchRoots}
     * (jars, or folders searched for jars). {@code driverClassSuffix} is the
     * class file's path suffix, e.g. {@code org/h2/Driver.class}.
     */
    static Connection withBorrowedDriver(String source, String driverLabel, List<Path> searchRoots, String driverClassSuffix,
            String url, Properties properties) throws ImportException {
        return withBorrowedDriver(source, driverLabel, searchRoots, driverClassSuffix, url, properties, Map.of());
    }

    /**
     * Like {@link #withBorrowedDriver(String, String, List, String, String, Properties)}, with system properties set
     * from loading the driver until the connection is closed - e.g. to keep HSQLDB from reconfiguring the server's
     * logging, which it does as late as when the database closes.
     */
    static Connection withBorrowedDriver(String source, String driverLabel, List<Path> searchRoots, String driverClassSuffix,
            String url, Properties properties, Map<String, String> systemProperties) throws ImportException {
        Map<String, String> previous = new HashMap<>();
        systemProperties.forEach((key, value) -> {
            previous.put(key, System.getProperty(key));
            System.setProperty(key, value);
        });
        Runnable restore = () -> previous.forEach((key, value) -> {
            if (value == null) {
                System.clearProperty(key);
            } else {
                System.setProperty(key, value);
            }
        });
        try {
            return connectWithBorrowedDriver(source, driverLabel, searchRoots, driverClassSuffix, url, properties, restore);
        } catch (ImportException | RuntimeException e) {
            restore.run();
            throw e;
        }
    }

    private static Connection connectWithBorrowedDriver(String source, String driverLabel, List<Path> searchRoots,
            String driverClassSuffix, String url, Properties properties, Runnable onClose) throws ImportException {
        List<DriverJar> candidates = findDriverJars(searchRoots, driverClassSuffix);
        if (candidates.isEmpty()) {
            throw new ImportException("moderation.import.error.driver", source, driverLabel);
        }
        SQLException last = null;
        for (DriverJar candidate : candidates) {
            URLClassLoader loader = null;
            try {
                loader = new URLClassLoader(new URL[] {candidate.jar().toUri().toURL()}, ClassLoader.getPlatformClassLoader());
                Driver driver = (Driver) Class.forName(candidate.className(), true, loader).getDeclaredConstructor().newInstance();
                Connection connection = driver.connect(url, properties);
                if (connection != null) {
                    return closingLoader(connection, loader, onClose);
                }
            } catch (SQLException e) {
                last = e;
            } catch (MalformedURLException | ReflectiveOperationException | LinkageError e) {
                last = new SQLException("Could not load " + candidate.className() + " from " + candidate.jar(), e);
            }
            closeQuietly(loader);
        }
        throw new ImportException("moderation.import.error.connect", last, source, last == null ? url : last.getMessage());
    }

    /**
     * The connection, with closing it also closing the class loader of the
     * borrowed driver - otherwise the source plugin's jar stays open (and on
     * Windows cannot be deleted) until the server restarts.
     */
    private static Connection closingLoader(Connection connection, URLClassLoader loader, Runnable onClose) {
        InvocationHandler handler = (proxy, method, args) -> {
            if (method.getName().equals("close") && method.getParameterCount() == 0) {
                try {
                    connection.close();
                } finally {
                    closeQuietly(loader);
                    onClose.run();
                }
                return null;
            }
            try {
                return method.invoke(connection, args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        };
        return (Connection) Proxy.newProxyInstance(ForeignDatabase.class.getClassLoader(), new Class<?>[] {Connection.class}, handler);
    }

    private static void closeQuietly(URLClassLoader loader) {
        if (loader == null) {
            return;
        }
        try {
            loader.close();
        } catch (IOException e) {
            // Nothing left to do - the jar is released when the loader is collected.
        }
    }

    /** Jars under {@code roots} containing a class whose path ends with {@code suffix}, newest file first. */
    static List<DriverJar> findDriverJars(List<Path> roots, String suffix) {
        List<DriverJar> found = new ArrayList<>();
        Set<Path> seen = new HashSet<>();
        for (Path root : roots) {
            if (root == null || !Files.exists(root)) {
                continue;
            }
            List<Path> jars;
            if (Files.isDirectory(root)) {
                try (Stream<Path> walk = Files.walk(root, MAX_DEPTH)) {
                    jars = walk.filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar")).toList();
                } catch (IOException e) {
                    continue;
                }
            } else {
                jars = List.of(root);
            }
            for (Path jar : jars) {
                if (seen.add(jar.toAbsolutePath().normalize())) {
                    driverClassIn(jar, suffix).ifPresent(className -> found.add(new DriverJar(jar, className)));
                }
            }
        }
        found.sort(Comparator.comparing(DriverJar::jar, Comparator.comparingLong(ForeignDatabase::lastModified)).reversed());
        return found;
    }

    private static Optional<String> driverClassIn(Path jar, String suffix) {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            return zip.stream()
                    .map(entry -> entry.getName())
                    .filter(name -> name.equals(suffix) || name.endsWith("/" + suffix))
                    .findFirst()
                    .map(name -> name.substring(0, name.length() - ".class".length()).replace('/', '.'));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private static long lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException e) {
            return 0;
        }
    }

    /** Lower-case column labels of a result set - sources differ between versions, so columns are read only if present. */
    static Set<String> columns(ResultSet resultSet) throws SQLException {
        ResultSetMetaData meta = resultSet.getMetaData();
        Set<String> columns = new HashSet<>();
        for (int i = 1; i <= meta.getColumnCount(); i++) {
            columns.add(meta.getColumnLabel(i).toLowerCase(Locale.ROOT));
        }
        return columns;
    }

    record DriverJar(Path jar, String className) {
    }
}

package dev.universaladmin.proxy;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.yaml.snakeyaml.Yaml;

/**
 * The proxy plugin's {@code config.yml} ({@code plugins/universaladmin-velocity/}
 * or {@code plugins/universaladmin-bungee/}), copied from the jar on first
 * start. A missing key falls back to the shipped default.
 */
public record ProxySettings(
        String host, int port, String database, String username, String password, boolean ssl, int poolSize,
        boolean banCheck, boolean allowOnError, String bannedPermanent, String bannedTemporary, String noReason,
        String datePattern, boolean maintenanceCheck, String maintenanceMotd, String maintenanceVersionText,
        String maintenanceMessage, boolean whitelistCheck, String notWhitelisted, boolean updateCheck) {

    public static ProxySettings load(Path dataDirectory) throws IOException {
        Path file = dataDirectory.resolve("config.yml");
        if (Files.notExists(file)) {
            Files.createDirectories(dataDirectory);
            try (InputStream defaults = ProxySettings.class.getResourceAsStream("/config.yml")) {
                Files.copy(defaults, file);
            }
        }
        Map<String, Object> defaults;
        try (InputStream in = ProxySettings.class.getResourceAsStream("/config.yml")) {
            defaults = new Yaml().load(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
        Map<String, Object> values;
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Map<String, Object> loaded = new Yaml().load(reader);
            values = loaded == null ? Map.of() : loaded;
        }
        Lookup config = new Lookup(values, defaults);
        return new ProxySettings(
                config.string("database.host"), config.integer("database.port"), config.string("database.database"),
                config.string("database.username"), config.string("database.password"), config.bool("database.ssl"),
                config.integer("database.pool-size"),
                config.bool("ban-check.enabled"), config.bool("ban-check.allow-on-error"),
                config.string("messages.banned-permanent"), config.string("messages.banned-temporary"),
                config.string("messages.no-reason"), config.string("messages.date-pattern"),
                config.bool("maintenance-check.enabled"), config.string("maintenance-check.motd"),
                config.string("maintenance-check.version-text"), config.string("messages.maintenance"),
                config.bool("whitelist-check.enabled"), config.string("messages.not-whitelisted"),
                config.bool("update-check.enabled"));
    }

    /** Whether the database connection was filled in - the shipped file has no password and the default host and user. */
    public boolean databaseConfigured() {
        return !(password.isEmpty() && "universaladmin".equals(username) && "localhost".equals(host));
    }

    /** Dotted-path reads over the loaded YAML, falling back to the defaults. */
    private record Lookup(Map<String, Object> values, Map<String, Object> defaults) {

        Object get(String path) {
            Object value = find(values, path);
            return value != null ? value : find(defaults, path);
        }

        String string(String path) {
            Object value = get(path);
            return value == null ? "" : value.toString();
        }

        int integer(String path) {
            Object value = get(path);
            return value instanceof Number number ? number.intValue() : Integer.parseInt(value.toString().trim());
        }

        boolean bool(String path) {
            Object value = get(path);
            return value instanceof Boolean flag ? flag : Boolean.parseBoolean(String.valueOf(value).trim());
        }

        @SuppressWarnings("unchecked")
        private static Object find(Map<String, Object> root, String path) {
            Object current = root;
            for (String part : path.split("\\.")) {
                if (!(current instanceof Map<?, ?> map)) {
                    return null;
                }
                current = ((Map<String, Object>) map).get(part);
            }
            return current;
        }
    }
}

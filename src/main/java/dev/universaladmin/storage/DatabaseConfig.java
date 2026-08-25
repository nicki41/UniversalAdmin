package dev.universaladmin.storage;

/**
 * Resolved {@code database:} section of {@code config.yml}. Only the fields
 * relevant to {@link #type} are meaningful; see docs/user/configuration.md
 * for the full YAML shape.
 */
public record DatabaseConfig(
        DatabaseType type,
        String sqliteFileName,
        String host,
        int port,
        String database,
        String username,
        String password,
        boolean ssl,
        int poolSize) {

    public static DatabaseConfig sqlite(String fileName) {
        return new DatabaseConfig(DatabaseType.SQLITE, fileName, null, 0, null, null, null, false, 5);
    }

    public static DatabaseConfig mysql(
            String host, int port, String database, String username, String password, boolean ssl, int poolSize) {
        return new DatabaseConfig(
                DatabaseType.MYSQL, null, host, port, database, username, password, ssl, poolSize);
    }

    /**
     * Redacts {@link #password}. Records generate a field-by-field {@code
     * toString()} by default, which would otherwise print the database
     * password in plain text the moment anything logs or prints a {@code
     * DatabaseConfig} - nothing does today, but this closes that off before
     * it becomes a one-line accident. See SECURITY.md's "Keine Secrets im
     * Log" rule.
     */
    @Override
    public String toString() {
        return "DatabaseConfig[type=" + type + ", sqliteFileName=" + sqliteFileName + ", host=" + host
                + ", port=" + port + ", database=" + database + ", username=" + username
                + ", password=" + (password == null || password.isEmpty() ? password : "***REDACTED***")
                + ", ssl=" + ssl + ", poolSize=" + poolSize + "]";
    }
}

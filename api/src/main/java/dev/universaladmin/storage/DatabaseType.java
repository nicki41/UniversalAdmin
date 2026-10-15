package dev.universaladmin.storage;

/** Supported database backends. SQLite is the default; see docs/architecture/storage.md. */
public enum DatabaseType {
    SQLITE,
    MYSQL
}

package dev.universaladmin.storage;

/**
 * Lifecycle state of {@link StorageService}'s underlying {@link javax.sql.DataSource}.
 *
 * <p>This is tracked once at construction (and reset on {@link StorageService#close()}),
 * not refreshed by a periodic live probe - see docs/architecture/storage.md#health
 * for why. {@link dev.universaladmin.core.UniversalAdmin#status()} maps this
 * into the coarser {@link dev.universaladmin.core.ComponentStatus} shown by
 * {@code /admin}.
 */
public enum DatabaseHealth {
    /** No connection pool has been created yet, or it has been closed. */
    DISCONNECTED,
    /** {@link StorageService} is creating and validating the connection pool. */
    CONNECTING,
    /** The connection pool was created and a validation query succeeded. */
    READY,
    /** Pool creation or validation failed. See docs/architecture/storage.md#health. */
    FAILED
}

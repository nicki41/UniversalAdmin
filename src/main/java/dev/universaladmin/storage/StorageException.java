package dev.universaladmin.storage;

/**
 * Generic unchecked wrapper for a {@link java.sql.SQLException} raised by
 * the storage foundation itself ({@link StorageService}, {@link Transactions}),
 * as opposed to a specific module's repository. A module's {@code jdbc}
 * package is free to keep its own dedicated exception type (e.g.
 * {@code PlayerStorageException}) instead - see docs/architecture/storage.md.
 */
public final class StorageException extends RuntimeException {

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}

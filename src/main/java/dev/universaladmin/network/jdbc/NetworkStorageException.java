package dev.universaladmin.network.jdbc;

/** A database error in network mode - the cause carries the SQL details. */
public final class NetworkStorageException extends RuntimeException {

    public NetworkStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}

package dev.universaladmin.modules.server;

/** Unchecked wrapper around a {@link java.sql.SQLException} from {@link dev.universaladmin.modules.server.jdbc.JdbcMaintenanceStateRepository}. */
public final class MaintenanceStorageException extends RuntimeException {

    public MaintenanceStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}

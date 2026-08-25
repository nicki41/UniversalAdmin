package dev.universaladmin.audit.jdbc;

/** Wraps a {@link java.sql.SQLException} from the audit repository as an unchecked exception. */
public final class AuditStorageException extends RuntimeException {

    public AuditStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}

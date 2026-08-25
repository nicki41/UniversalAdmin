package dev.universaladmin.modules.moderation.jdbc;

/** Unchecked wrapper around a {@link java.sql.SQLException} from {@link JdbcStaffModeSnapshotRepository}. */
public final class StaffModeStorageException extends RuntimeException {

    public StaffModeStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}

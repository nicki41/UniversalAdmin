package dev.universaladmin.modules.moderation.jdbc;

/** Unchecked wrapper around a {@link java.sql.SQLException} from {@link JdbcPunishmentRepository}. */
public final class PunishmentStorageException extends RuntimeException {

    public PunishmentStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}

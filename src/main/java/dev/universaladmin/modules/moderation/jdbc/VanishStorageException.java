package dev.universaladmin.modules.moderation.jdbc;

/** Unchecked wrapper around a {@link java.sql.SQLException} from {@link JdbcVanishRepository}. */
public final class VanishStorageException extends RuntimeException {

    public VanishStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}

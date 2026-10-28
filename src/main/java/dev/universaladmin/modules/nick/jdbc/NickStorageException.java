package dev.universaladmin.modules.nick.jdbc;

/** Unchecked wrapper around a {@link java.sql.SQLException} from {@link JdbcNickRepository}. */
public final class NickStorageException extends RuntimeException {

    public NickStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}

package dev.universaladmin.modules.players.jdbc;

/** Wraps a {@link java.sql.SQLException} from the player profile repository as an unchecked exception. */
public final class PlayerStorageException extends RuntimeException {

    public PlayerStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}

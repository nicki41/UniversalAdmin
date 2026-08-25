package dev.universaladmin.modules.whitelist;

/** Unchecked wrapper around a {@link java.sql.SQLException} from {@link dev.universaladmin.modules.whitelist.jdbc.JdbcWhitelistEntryRepository}. */
public final class WhitelistStorageException extends RuntimeException {

    public WhitelistStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}

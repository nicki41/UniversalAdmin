package dev.universaladmin.modules.moderation;

/** Thrown by {@link DurationParser#parse} for text that isn't a valid duration - a normal user-input error, not a bug. */
public final class DurationParseException extends RuntimeException {

    public DurationParseException(String message) {
        super(message);
    }
}

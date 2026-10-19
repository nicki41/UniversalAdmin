package dev.universaladmin.localization;

/** A dotted lookup key into the active locale's message file, e.g. {@code players.kicked}. */
public record MessageKey(String value) {

    public static MessageKey of(String value) {
        return new MessageKey(value);
    }

    @Override
    public String toString() {
        return value;
    }
}

package dev.universaladmin.localization;

/**
 * Looks up user-facing text by {@link MessageKey} instead of hardcoding
 * strings in GUI/command/action code, so messages can be translated and
 * (later) reused by the web app without duplicating copy.
 */
public interface MessageService {

    /** Resolves {@code key} in the active locale, substituting {@code args} with {@link java.text.MessageFormat}. */
    String get(MessageKey key, Object... args);
}

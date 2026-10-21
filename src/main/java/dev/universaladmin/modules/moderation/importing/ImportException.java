package dev.universaladmin.modules.moderation.importing;

import dev.universaladmin.localization.MessageKey;
import java.util.List;

/**
 * An import that cannot start or cannot finish, with a text for the user
 * ({@code moderation.import.error.*}) - a missing database driver, a wrong
 * password in the source plugin's config, an unreadable file.
 */
public final class ImportException extends Exception {

    private final transient MessageKey messageKey;
    private final transient List<Object> messageArgs;

    public ImportException(String messageKey, Throwable cause, Object... args) {
        super(messageKey + (cause == null ? "" : ": " + cause.getMessage()), cause);
        this.messageKey = MessageKey.of(messageKey);
        this.messageArgs = List.of(args);
    }

    public ImportException(String messageKey, Object... args) {
        this(messageKey, null, args);
    }

    public MessageKey messageKey() {
        return messageKey;
    }

    public Object[] messageArgs() {
        return messageArgs.toArray();
    }
}

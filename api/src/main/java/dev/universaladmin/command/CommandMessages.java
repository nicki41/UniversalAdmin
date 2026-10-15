package dev.universaladmin.command;

import dev.universaladmin.localization.MessageKey;

/** Message keys shared by every command - see {@code command.*} in {@code lang/en_US.yml}. */
public final class CommandMessages {

    /** Prepended to every {@link CommandInvocation#reply}; server owners may set it to {@code ""}. */
    public static final MessageKey PREFIX = MessageKey.of("command.prefix");
    public static final MessageKey USAGE = MessageKey.of("command.usage");
    public static final MessageKey NO_PERMISSION = MessageKey.of("error.no-permission");
    public static final MessageKey PLAYER_ONLY = MessageKey.of("command.error.player-only");
    public static final MessageKey UNKNOWN = MessageKey.of("command.error.unknown");
    public static final MessageKey NOT_FOUND = MessageKey.of("command.error.not-found");
    public static final MessageKey DISABLED = MessageKey.of("command.error.disabled");
    public static final MessageKey INVALID = MessageKey.of("command.error.invalid");
    public static final MessageKey INTERNAL_ERROR = MessageKey.of("command.error.internal");
    public static final MessageKey PLAYER_NOT_FOUND = MessageKey.of("command.error.player-not-found");
    public static final MessageKey PLAYER_NOT_ONLINE = MessageKey.of("command.error.player-not-online");
    public static final MessageKey INVALID_NUMBER = MessageKey.of("command.error.invalid-number");
    public static final MessageKey INVALID_DURATION = MessageKey.of("command.error.invalid-duration");
    public static final MessageKey WORLD_NOT_FOUND = MessageKey.of("command.error.world-not-found");
    public static final MessageKey CONFIRM_REQUIRED = MessageKey.of("command.error.confirm-required");
    public static final MessageKey DONE = MessageKey.of("command.done");

    private CommandMessages() {
    }
}

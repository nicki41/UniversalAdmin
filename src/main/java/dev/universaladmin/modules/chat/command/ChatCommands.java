package dev.universaladmin.modules.chat.command;

import dev.universaladmin.command.AdminCommand;
import dev.universaladmin.command.BaseAdminCommand;
import dev.universaladmin.command.CommandInvocation;
import dev.universaladmin.core.time.DurationParser;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.localization.MessageService;
import dev.universaladmin.modules.chat.ChatPermissions;
import dev.universaladmin.modules.chat.ChatService;
import dev.universaladmin.modules.chat.action.ChatActions;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** {@code /staffchat [message]} (alias {@code /sc}) and {@code /admin chat <lock|unlock|slow|clear|status>}. */
public final class ChatCommands {

    private ChatCommands() {
    }

    public static List<AdminCommand> all(ChatService chat) {
        return List.of(new StaffChat(), new Chat(chat));
    }

    /** With a message: sends it to staff chat. Without: toggles "everything I type goes to staff chat". */
    static final class StaffChat extends BaseAdminCommand {

        StaffChat() {
            super("staffchat", ChatPermissions.STAFF_CHAT, MessageKey.of("commands.staffchat.usage"),
                    MessageKey.of("commands.staffchat.description"));
            aliases("sc");
            shortcuts("staffchat", "sc");
            shortcutsAlwaysRegistered();
        }

        @Override
        public void execute(CommandInvocation invocation) {
            if (invocation.argCount() > 0) {
                invocation.<String, Void>run(ChatActions.STAFF_MESSAGE, invocation.joinArgs(0), ignored -> {
                });
                return;
            }
            if (invocation.player().isEmpty()) {
                invocation.usage(this);
                return;
            }
            invocation.run(ChatActions.STAFF_TOGGLE, null);
        }
    }

    /** {@code /admin chat <lock|unlock|slow <duration|off>|clear|status>}. */
    static final class Chat extends BaseAdminCommand {

        private static final List<String> SUBCOMMANDS = List.of("lock", "unlock", "slow", "clear", "status");

        private final ChatService chat;

        Chat(ChatService chat) {
            super("chat", ChatPermissions.MANAGE, MessageKey.of("commands.chat.usage"), MessageKey.of("commands.chat.description"));
            this.chat = chat;
        }

        @Override
        public void execute(CommandInvocation invocation) {
            String sub = invocation.arg(0).map(arg -> arg.toLowerCase(Locale.ROOT)).orElse("status");
            switch (sub) {
                // Lock/unlock/slow mode announce themselves to everyone, the
                // sender included - no second line needed.
                case "lock" -> invocation.<Void, Boolean>run(ChatActions.LOCK, null, ignored -> { });
                case "unlock" -> invocation.<Void, Boolean>run(ChatActions.UNLOCK, null, ignored -> { });
                case "slow", "slowmode" -> slow(invocation);
                case "clear" -> invocation.run(ChatActions.CLEAR, null);
                case "status" -> invocation.reply("chat.status",
                        MessageService.markup(invocation.messages().get(MessageKey.of(chat.locked() ? "chat.state-locked" : "chat.state-open"))),
                        chat.slowMode().isZero() ? invocation.messages().get(MessageKey.of("chat.slowmode-none"))
                                : DurationParser.format(chat.slowMode()));
                default -> invocation.usage(this);
            }
        }

        private void slow(CommandInvocation invocation) {
            if (invocation.argCount() < 2) {
                invocation.usage(this);
                return;
            }
            String raw = invocation.args().get(1);
            Duration delay;
            if (raw.equalsIgnoreCase("off") || raw.equals("0")) {
                delay = Duration.ZERO;
            } else {
                Optional<Duration> parsed = invocation.duration(1);
                if (parsed.isEmpty()) {
                    return;
                }
                delay = parsed.get();
            }
            invocation.<Duration, Duration>run(ChatActions.SLOW_MODE, delay, ignored -> {
            });
        }

        @Override
        public List<String> complete(CommandInvocation invocation) {
            if (invocation.argCount() <= 1) {
                return SUBCOMMANDS;
            }
            return invocation.argCount() == 2 && invocation.args().get(0).toLowerCase(Locale.ROOT).startsWith("slow")
                    ? List.of("off", "3s", "5s", "10s", "30s", "1m")
                    : List.of();
        }
    }
}

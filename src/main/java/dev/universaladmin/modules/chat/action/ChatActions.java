package dev.universaladmin.modules.chat.action;

import dev.universaladmin.action.Action;
import dev.universaladmin.action.ActionContext;
import dev.universaladmin.action.ActionId;
import dev.universaladmin.action.ActionResult;
import dev.universaladmin.action.ActionResult.FailureReason;
import dev.universaladmin.core.time.DurationParser;
import dev.universaladmin.localization.ComponentMessages;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.localization.MessageService;
import dev.universaladmin.modules.chat.ChatPermissions;
import dev.universaladmin.modules.chat.ChatService;
import dev.universaladmin.modules.chat.ChatSettings;
import dev.universaladmin.network.NetworkService;
import dev.universaladmin.scheduler.TaskScheduler;
import dev.universaladmin.settings.SettingsService;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Every action of the Chat module. Each one changes {@link ChatService}
 * state and/or delivers messages on the main thread; frontends (commands,
 * the chat GUI, the chat listener for staff chat) only call these.
 */
public final class ChatActions {

    public static final ActionId LOCK = ActionId.core("chat.lock");
    public static final ActionId UNLOCK = ActionId.core("chat.unlock");
    public static final ActionId SLOW_MODE = ActionId.core("chat.slowmode");
    public static final ActionId CLEAR = ActionId.core("chat.clear");
    public static final ActionId STAFF_MESSAGE = ActionId.core("chat.staff.message");
    public static final ActionId STAFF_TOGGLE = ActionId.core("chat.staff.toggle");

    private ChatActions() {
    }

    /** Locks or unlocks the public chat and tells everyone. Result: whether anything changed. */
    public static final class SetLock implements Action<Void, Boolean> {

        private final boolean lock;
        private final ChatService chat;
        private final MessageService messages;
        private final TaskScheduler scheduler;

        public SetLock(boolean lock, ChatService chat, MessageService messages, TaskScheduler scheduler) {
            this.lock = lock;
            this.chat = chat;
            this.messages = messages;
            this.scheduler = scheduler;
        }

        @Override
        public ActionId id() {
            return lock ? LOCK : UNLOCK;
        }

        @Override
        public CompletableFuture<ActionResult<Boolean>> execute(ActionContext context, Void input) {
            if (!chat.setLocked(lock)) {
                return CompletableFuture.completedFuture(ActionResult.failure(FailureReason.CONFLICT,
                        MessageKey.of(lock ? "chat.already-locked" : "chat.already-unlocked")));
            }
            String text = messages.get(MessageKey.of(lock ? "chat.locked-broadcast" : "chat.unlocked-broadcast"),
                    context.actor().displayName());
            scheduler.runOnMainThread(() -> Bukkit.broadcast(ComponentMessages.render(text)));
            return CompletableFuture.completedFuture(ActionResult.success(true));
        }
    }

    /** Sets slow mode ({@link Duration#ZERO} = off) and tells everyone. */
    public static final class SetSlowMode implements Action<Duration, Duration> {

        private final ChatService chat;
        private final MessageService messages;
        private final TaskScheduler scheduler;

        public SetSlowMode(ChatService chat, MessageService messages, TaskScheduler scheduler) {
            this.chat = chat;
            this.messages = messages;
            this.scheduler = scheduler;
        }

        @Override
        public ActionId id() {
            return SLOW_MODE;
        }

        @Override
        public CompletableFuture<ActionResult<Duration>> execute(ActionContext context, Duration delay) {
            chat.setSlowMode(delay);
            String text = delay.isZero()
                    ? messages.get(MessageKey.of("chat.slowmode-off-broadcast"), context.actor().displayName())
                    : messages.get(MessageKey.of("chat.slowmode-broadcast"), DurationParser.format(delay), context.actor().displayName());
            scheduler.runOnMainThread(() -> Bukkit.broadcast(ComponentMessages.render(text)));
            return CompletableFuture.completedFuture(ActionResult.success(delay));
        }
    }

    /** Pushes the chat history of everyone without {@link ChatPermissions#BYPASS} out of view. */
    public static final class Clear implements Action<Void, Integer> {

        private final MessageService messages;
        private final SettingsService settings;
        private final TaskScheduler scheduler;

        public Clear(MessageService messages, SettingsService settings, TaskScheduler scheduler) {
            this.messages = messages;
            this.settings = settings;
            this.scheduler = scheduler;
        }

        @Override
        public ActionId id() {
            return CLEAR;
        }

        @Override
        public CompletableFuture<ActionResult<Integer>> execute(ActionContext context, Void input) {
            int lines = settings.get(ChatSettings.CLEAR_LINES);
            Component notice = ComponentMessages.render(messages.get(MessageKey.of("chat.cleared-broadcast"), context.actor().displayName()));
            return scheduler.supplyOnMainThread(() -> {
                int cleared = 0;
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (!player.hasPermission(ChatPermissions.BYPASS.value())) {
                        for (int i = 0; i < lines; i++) {
                            player.sendMessage(Component.empty());
                        }
                        cleared++;
                    }
                    player.sendMessage(notice);
                }
                return ActionResult.success(cleared, MessageKey.of("chat.clear-done"), Integer.toString(cleared));
            });
        }
    }

    /**
     * Sends one staff chat message to every online player with
     * {@link ChatPermissions#STAFF_CHAT} - in network mode on every server,
     * tagged there with the name of the server it came from.
     */
    public static final class StaffMessage implements Action<String, Void> {

        /** The network channel staff chat travels on between servers. */
        public static final String NETWORK_TOPIC = "chat.staff";

        private final MessageService messages;
        private final SettingsService settings;
        private final TaskScheduler scheduler;
        private final Logger logger;
        private final NetworkService network;

        public StaffMessage(MessageService messages, SettingsService settings, TaskScheduler scheduler, Logger logger, NetworkService network) {
            this.messages = messages;
            this.settings = settings;
            this.scheduler = scheduler;
            this.logger = logger;
            this.network = network;
        }

        @Override
        public ActionId id() {
            return STAFF_MESSAGE;
        }

        @Override
        public CompletableFuture<ActionResult<Void>> execute(ActionContext context, String message) {
            String sender = context.actor().displayName();
            deliver(sender, message.trim(), null);
            network.publish(NETWORK_TOPIC, Map.of("sender", sender, "message", message.trim()));
            return CompletableFuture.completedFuture(ActionResult.success(null));
        }

        /** Shows a staff chat line to the staff on this server; {@code server} is where it was written, {@code null} for here. */
        public void deliver(String sender, String message, String server) {
            String text = server == null
                    ? messages.get(MessageKey.of("chat.staff.format"), sender, message)
                    : messages.get(MessageKey.of("chat.staff.format-network"), sender, message, server);
            Component rendered = ComponentMessages.render(text);
            boolean console = settings.get(ChatSettings.STAFF_CHAT_CONSOLE);
            scheduler.runOnMainThread(() -> {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.hasPermission(ChatPermissions.STAFF_CHAT.value())) {
                        player.sendMessage(rendered);
                    }
                }
                if (console) {
                    logger.info("[Staff]" + (server == null ? "" : " [" + server + "]") + " " + sender + ": " + message);
                }
            });
        }
    }

    /** Switches "everything I type goes to staff chat" for the actor. Result: the new state. */
    public static final class ToggleStaffChat implements Action<Void, Boolean> {

        private final ChatService chat;

        public ToggleStaffChat(ChatService chat) {
            this.chat = chat;
        }

        @Override
        public ActionId id() {
            return STAFF_TOGGLE;
        }

        @Override
        public CompletableFuture<ActionResult<Boolean>> execute(ActionContext context, Void input) {
            UUID playerId = context.actor().playerId();
            if (playerId == null) {
                return CompletableFuture.completedFuture(ActionResult.failure(FailureReason.VALIDATION, MessageKey.of("command.error.player-only")));
            }
            boolean enabled = chat.toggleStaffChat(playerId);
            return CompletableFuture.completedFuture(ActionResult.success(enabled, MessageKey.of("chat.staff.toggled"),
                    MessageKey.of(enabled ? "moderation.gui.status.on" : "moderation.gui.status.off")));
        }
    }
}

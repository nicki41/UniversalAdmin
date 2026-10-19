package dev.universaladmin.modules.chat;

import dev.universaladmin.action.ActionContext;
import dev.universaladmin.action.ActionExecutor;
import dev.universaladmin.action.Actor;
import dev.universaladmin.action.Source;
import dev.universaladmin.core.time.DurationParser;
import dev.universaladmin.localization.ComponentMessages;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.localization.MessageService;
import dev.universaladmin.modules.chat.action.ChatActions;
import dev.universaladmin.permission.bukkit.PermissiblePermissionEvaluator;
import dev.universaladmin.settings.SettingsService;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Translates chat events into {@link ChatService} decisions: staff chat
 * (toggled, or a message starting with {@code chat.staff-chat.prefix}) is
 * routed to {@link ChatActions#STAFF_MESSAGE}; chat lock and slow mode
 * cancel a public message with a short explanation. Runs early (LOW) so
 * staff chat never reaches public chat, and before the Moderation module's
 * mute check, which skips messages already cancelled here.
 */
public final class ChatListener implements Listener {

    private final ChatService chat;
    private final ActionExecutor actions;
    private final SettingsService settings;
    private final MessageService messages;

    public ChatListener(ChatService chat, ActionExecutor actions, SettingsService settings, MessageService messages) {
        this.chat = chat;
        this.actions = actions;
        this.settings = settings;
        this.messages = messages;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        String plain = PlainTextComponentSerializer.plainText().serialize(event.message());

        if (player.hasPermission(ChatPermissions.STAFF_CHAT.value())) {
            String prefix = settings.get(ChatSettings.STAFF_CHAT_PREFIX);
            boolean prefixed = !prefix.isEmpty() && plain.startsWith(prefix) && plain.length() > prefix.length();
            if (prefixed || chat.staffChatToggled(player.getUniqueId())) {
                event.setCancelled(true);
                String message = prefixed ? plain.substring(prefix.length()) : plain;
                if (!message.isBlank()) {
                    actions.execute(ChatActions.STAFF_MESSAGE, contextFor(player), message);
                }
                return;
            }
        }

        switch (chat.check(player.getUniqueId(), player.hasPermission(ChatPermissions.BYPASS.value()))) {
            case ChatService.Decision.Allowed allowed -> {
            }
            case ChatService.Decision.Locked locked -> {
                event.setCancelled(true);
                player.sendMessage(ComponentMessages.render(messages.get(MessageKey.of("chat.locked-blocked"))));
            }
            case ChatService.Decision.SlowMode slow -> {
                event.setCancelled(true);
                player.sendMessage(ComponentMessages.render(messages.get(MessageKey.of("chat.slowmode-blocked"),
                        DurationParser.format(slow.retryAfter().plusSeconds(1)))));
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        chat.forget(event.getPlayer().getUniqueId());
    }

    private static ActionContext contextFor(Player player) {
        return new ActionContext(Actor.player(player.getUniqueId(), player.getName(), new PermissiblePermissionEvaluator(player)),
                Source.COMMAND);
    }
}

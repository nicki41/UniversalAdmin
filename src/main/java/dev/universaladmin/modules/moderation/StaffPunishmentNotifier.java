package dev.universaladmin.modules.moderation;

import dev.universaladmin.action.ActionEvent;
import dev.universaladmin.action.ActionEventListener;
import dev.universaladmin.action.ActionId;
import dev.universaladmin.core.time.DurationParser;
import dev.universaladmin.localization.ComponentMessages;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.localization.MessageService;
import dev.universaladmin.modules.moderation.action.ModerationActionIds;
import dev.universaladmin.scheduler.TaskScheduler;
import dev.universaladmin.settings.SettingsService;
import dev.universaladmin.status.MuteKind;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Tells every other online staff member ({@link ModerationPermissions#NOTIFY})
 * when someone is punished or pardoned - "Mod banned Steve (7d): griefing" -
 * so the whole team sees what happens without reading the audit log. Driven
 * by the action pipeline's {@link ActionEvent.Executed} events, so a
 * punishment from a command, the GUI or an extension is announced the same
 * way; in network mode {@link NetworkModerationBridge} announces the ones
 * from other servers too, tagged with the server's name. Switch off with
 * {@code moderation.notify-staff}.
 */
public final class StaffPunishmentNotifier implements ActionEventListener {

    private static final Map<ActionId, String> KEYS = Map.ofEntries(
            Map.entry(ModerationActionIds.KICK, "moderation.notify.kick"),
            Map.entry(ModerationActionIds.BAN, "moderation.notify.ban"),
            Map.entry(ModerationActionIds.TEMP_BAN, "moderation.notify.tempban"),
            Map.entry(ModerationActionIds.IP_BAN, "moderation.notify.ipban"),
            Map.entry(ModerationActionIds.MUTE, "moderation.notify.mute"),
            Map.entry(ModerationActionIds.TEMP_MUTE, "moderation.notify.tempmute"),
            Map.entry(ModerationActionIds.WARN, "moderation.notify.warn"),
            Map.entry(ModerationActionIds.FREEZE, "moderation.notify.freeze"),
            Map.entry(ModerationActionIds.UNBAN, "moderation.notify.unban"),
            Map.entry(ModerationActionIds.UNMUTE, "moderation.notify.unmute"),
            Map.entry(ModerationActionIds.UNFREEZE, "moderation.notify.unfreeze"),
            Map.entry(ModerationActionIds.REMOVE_WARN, "moderation.notify.removewarn"));

    /** The announcements that name the kind of a chat- or voice-only mute ({@code moderation.notify.mute-voice}, ...). */
    private static final Set<ActionId> ISSUED_MUTES = Set.of(ModerationActionIds.MUTE, ModerationActionIds.TEMP_MUTE);

    private final MessageService messages;
    private final SettingsService settings;
    private final TaskScheduler scheduler;

    public StaffPunishmentNotifier(MessageService messages, SettingsService settings, TaskScheduler scheduler) {
        this.messages = messages;
        this.settings = settings;
        this.scheduler = scheduler;
    }

    /** Whether {@code id} is one of the actions this notifier announces. */
    static boolean announces(ActionId id) {
        return KEYS.containsKey(id);
    }

    @Override
    public void onActionEvent(ActionEvent event) {
        if (!(event instanceof ActionEvent.Executed<?, ?> executed)) {
            return;
        }
        Punishment punishment = punishmentOf(executed.result().value());
        if (punishment != null) {
            announce(executed.id(), punishment, executed.context().actor().displayName(), executed.context().actor().playerId(), null);
        }
    }

    /**
     * Sends the announcement to every staff member online here except the
     * one who acted.
     *
     * @param server the server it happened on, {@code null} for this one
     */
    void announce(ActionId id, Punishment punishment, String actorName, UUID actorId, String server) {
        String key = KEYS.get(id);
        if (key == null || !settings.get(ModerationSettings.NOTIFY_STAFF)) {
            return;
        }
        if (punishment.isMute() && punishment.muteKind() != MuteKind.FULL && ISSUED_MUTES.contains(id)) {
            key = key + (punishment.muteKind() == MuteKind.CHAT ? "-chat" : "-voice");
        }
        String duration = punishment.expiresAt() == null
                ? messages.get(MessageKey.of("moderation.gui.permanent"))
                : DurationParser.formatRemaining(punishment.expiresAt());
        String text = messages.get(MessageKey.of(key), actorName,
                punishment.targetLastKnownName(), punishment.reason() == null ? "-" : punishment.reason(), duration);
        if (server != null) {
            text = messages.get(MessageKey.of("moderation.notify.from-server"), server) + text;
        }
        Component rendered = ComponentMessages.render(text);
        scheduler.runOnMainThread(() -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!player.getUniqueId().equals(actorId) && player.hasPermission(ModerationPermissions.NOTIFY.value())) {
                    player.sendMessage(rendered);
                }
            }
        });
    }

    /** A punishing action returns the {@link Punishment}; a pardoning one the list of punishments it lifted. */
    static Punishment punishmentOf(Object value) {
        if (value instanceof Punishment punishment) {
            return punishment;
        }
        if (value instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Punishment punishment) {
            return punishment;
        }
        return null;
    }
}

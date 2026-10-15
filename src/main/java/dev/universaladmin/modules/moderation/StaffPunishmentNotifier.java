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
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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
 * way. Switch off with {@code moderation.notify-staff}.
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

    private final MessageService messages;
    private final SettingsService settings;
    private final TaskScheduler scheduler;

    public StaffPunishmentNotifier(MessageService messages, SettingsService settings, TaskScheduler scheduler) {
        this.messages = messages;
        this.settings = settings;
        this.scheduler = scheduler;
    }

    @Override
    public void onActionEvent(ActionEvent event) {
        if (!(event instanceof ActionEvent.Executed<?, ?> executed)) {
            return;
        }
        String key = KEYS.get(executed.id());
        if (key == null || !settings.get(ModerationSettings.NOTIFY_STAFF)) {
            return;
        }
        Punishment punishment = punishmentOf(executed.result().value());
        if (punishment == null) {
            return;
        }
        String duration = punishment.expiresAt() == null
                ? messages.get(MessageKey.of("moderation.gui.permanent"))
                : DurationParser.format(Duration.between(Instant.now(), punishment.expiresAt()).plusSeconds(1));
        String text = messages.get(MessageKey.of(key), executed.context().actor().displayName(),
                punishment.targetLastKnownName(), punishment.reason() == null ? "-" : punishment.reason(), duration);
        UUID actorId = executed.context().actor().playerId();
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
    private static Punishment punishmentOf(Object value) {
        if (value instanceof Punishment punishment) {
            return punishment;
        }
        if (value instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Punishment punishment) {
            return punishment;
        }
        return null;
    }
}

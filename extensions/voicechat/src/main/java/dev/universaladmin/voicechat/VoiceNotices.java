package dev.universaladmin.voicechat;

import dev.universaladmin.core.time.DurationParser;
import dev.universaladmin.localization.ComponentMessages;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.localization.MessageService;
import dev.universaladmin.scheduler.TaskScheduler;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Tells a player why nobody hears them - in the action bar, at most once
 * every few seconds: microphone packets come many times a second, a message
 * per packet would flood the client.
 */
final class VoiceNotices {

    static final Duration INTERVAL = Duration.ofSeconds(4);

    private final MessageService messages;
    private final TaskScheduler scheduler;
    private final LongSupplier clock;
    private final Map<UUID, Long> lastShown = new ConcurrentHashMap<>();

    VoiceNotices(MessageService messages, TaskScheduler scheduler) {
        this(messages, scheduler, System::nanoTime);
    }

    VoiceNotices(MessageService messages, TaskScheduler scheduler, LongSupplier clock) {
        this.messages = messages;
        this.scheduler = scheduler;
        this.clock = clock;
    }

    /** Shows {@code block}'s notice to the player unless they saw one within {@link #INTERVAL}. */
    void show(UUID playerId, VoiceGate.Block block) {
        if (!due(playerId)) {
            return;
        }
        Component notice = ComponentMessages.render(text(block));
        scheduler.runOnMainThread(() -> {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                player.sendActionBar(notice);
            }
        });
    }

    /** Whether the player's next notice may be shown now - and if so, counts it as shown. */
    boolean due(UUID playerId) {
        long now = clock.getAsLong();
        boolean[] due = {false};
        lastShown.compute(playerId, (id, last) -> {
            if (last == null || now - last >= INTERVAL.toNanos()) {
                due[0] = true;
                return now;
            }
            return last;
        });
        return due[0];
    }

    /** Forgets a player who left. */
    void forget(UUID playerId) {
        lastShown.remove(playerId);
    }

    private String text(VoiceGate.Block block) {
        if (block.reason() == VoiceGate.Reason.VANISHED) {
            return messages.get(MessageKey.of("voicechat.notice.vanished"));
        }
        String reason = block.mute().reason() == null || block.mute().reason().isBlank() ? "-" : block.mute().reason();
        return block.mute().permanent()
                ? messages.get(MessageKey.of("voicechat.notice.muted-permanent"), reason)
                : messages.get(MessageKey.of("voicechat.notice.muted-temp"), reason, DurationParser.formatRemaining(block.mute().expiresAt()));
    }
}

package dev.universaladmin.modules.moderation;

import dev.universaladmin.core.time.DurationParser;
import dev.universaladmin.localization.ComponentMessages;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.localization.MessageService;
import dev.universaladmin.settings.SettingsService;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.Plugin;

/**
 * What a freeze looks like: the frozen player sees a title until they are
 * free ({@code moderation.freeze.show-title}), and staff who may freeze see
 * a marker above their head with who froze them and since when
 * ({@code moderation.freeze.staff-marker}). The marker is a {@link TextDisplay}
 * hidden from everyone by default and shown per viewer
 * ({@link Player#showEntity}) - Paper API, no packets. It is never saved with
 * the world. Runs once a second on the main thread.
 */
public final class FreezeDisplay implements Runnable, AutoCloseable {

    private static final Title.Times FIRST = Title.Times.times(Duration.ofMillis(250), Duration.ofMillis(2500), Duration.ofMillis(500));
    private static final Title.Times AGAIN = Title.Times.times(Duration.ZERO, Duration.ofMillis(2500), Duration.ofMillis(500));
    /** Above the name tag. */
    private static final double ABOVE_HEAD = 0.8;

    private final Plugin plugin;
    private final FreezeRuntimeState state;
    private final SettingsService settings;
    private final MessageService messages;
    private final Map<UUID, Marker> markers = new HashMap<>();
    private final Set<UUID> titled = new HashSet<>();

    public FreezeDisplay(Plugin plugin, FreezeRuntimeState state, SettingsService settings, MessageService messages) {
        this.plugin = plugin;
        this.state = state;
        this.settings = settings;
        this.messages = messages;
    }

    private static final class Marker {
        private final TextDisplay display;
        /** By instance, not by id: a viewer who rejoins is a new player who has not been shown the marker yet. */
        private final Set<Player> viewers = Collections.newSetFromMap(new IdentityHashMap<>());

        private Marker(TextDisplay display) {
            this.display = display;
        }
    }

    @Override
    public void run() {
        boolean showTitle = settings.get(ModerationSettings.FREEZE_SHOW_TITLE);
        boolean showMarker = settings.get(ModerationSettings.FREEZE_STAFF_MARKER);
        Collection<? extends Player> online = plugin.getServer().getOnlinePlayers();
        Set<UUID> frozen = new HashSet<>();
        for (Player player : online) {
            Optional<FreezeRuntimeState.Freeze> freeze = state.freeze(player.getUniqueId());
            if (freeze.isEmpty()) {
                continue;
            }
            frozen.add(player.getUniqueId());
            if (showTitle) {
                title(player, titled.add(player.getUniqueId()));
            }
            if (showMarker) {
                marker(player, freeze.get(), online);
            }
        }
        titled.removeIf(id -> {
            if (showTitle && frozen.contains(id)) {
                return false;
            }
            Player player = plugin.getServer().getPlayer(id);
            if (player != null) {
                player.clearTitle();
            }
            return true;
        });
        markers.entrySet().removeIf(entry -> {
            if (showMarker && frozen.contains(entry.getKey())) {
                return false;
            }
            entry.getValue().display.remove();
            return true;
        });
    }

    private void title(Player player, boolean first) {
        player.showTitle(Title.title(render("moderation.freeze.title"), render("moderation.freeze.subtitle"), first ? FIRST : AGAIN));
    }

    private void marker(Player player, FreezeRuntimeState.Freeze freeze, Collection<? extends Player> online) {
        Location at = player.getLocation().add(0, player.getHeight() + ABOVE_HEAD, 0);
        Marker marker = markers.get(player.getUniqueId());
        if (marker == null || !marker.display.isValid()) {
            if (marker != null) {
                marker.display.remove();
            }
            marker = new Marker(player.getWorld().spawn(at, TextDisplay.class, display -> {
                display.setPersistent(false);
                display.setVisibleByDefault(false);
                display.setBillboard(Display.Billboard.CENTER);
                display.setTeleportDuration(10);
            }));
            markers.put(player.getUniqueId(), marker);
        } else if (!marker.display.getWorld().equals(at.getWorld()) || marker.display.getLocation().distanceSquared(at) > 0.01) {
            marker.display.teleport(at);
        }
        String since = since(freeze.since());
        marker.display.text(freeze.by().isEmpty()
                ? render("moderation.freeze.marker-unknown", since)
                : render("moderation.freeze.marker", freeze.by(), since));
        marker.viewers.removeIf(viewer -> !viewer.isOnline());
        for (Player viewer : online) {
            boolean staff = !viewer.equals(player) && viewer.hasPermission(ModerationPermissions.FREEZE.value());
            if (staff && marker.viewers.add(viewer)) {
                viewer.showEntity(plugin, marker.display);
            } else if (!staff && marker.viewers.remove(viewer)) {
                viewer.hideEntity(plugin, marker.display);
            }
        }
    }

    /** Seconds in the first minute, then whole minutes: {@code 45s}, {@code 2m}, {@code 1h 5m}. */
    static String since(Instant since) {
        long seconds = Math.max(0, Duration.between(since, Instant.now()).toSeconds());
        return DurationParser.format(Duration.ofSeconds(seconds < 60 ? seconds : seconds - seconds % 60));
    }

    private Component render(String key, Object... args) {
        return ComponentMessages.render(messages.get(MessageKey.of(key), args));
    }

    /** Takes every marker away and every title down - on disable, so nothing is left behind. */
    @Override
    public void close() {
        markers.values().forEach(marker -> marker.display.remove());
        markers.clear();
        for (UUID id : titled) {
            Player player = plugin.getServer().getPlayer(id);
            if (player != null) {
                player.clearTitle();
            }
        }
        titled.clear();
    }
}

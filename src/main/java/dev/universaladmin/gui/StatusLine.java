package dev.universaladmin.gui;

import dev.universaladmin.settings.CoreSettings;
import dev.universaladmin.settings.SettingsService;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Server;
import org.bukkit.entity.Player;

/**
 * The status line: what a staff member has switched on - the
 * {@link StatusToggle}s of the main menu's "My status" row that are on -
 * in their action bar, e.g. "Vanish · Godmode · Nick Notch". Nothing is
 * shown while nothing is on, nor while another action bar message holds it
 * back ({@link StatusToggleRegistry#pauseStatusLine}). Runs once a second on
 * the main thread; off with {@code gui.status-line: false}.
 */
public final class StatusLine implements Runnable {

    private static final Component SEPARATOR = Component.text(" · ", NamedTextColor.DARK_GRAY);

    private final Server server;
    private final StatusToggleRegistry toggles;
    private final SettingsService settings;
    private final Logger logger;

    public StatusLine(Server server, StatusToggleRegistry toggles, SettingsService settings, Logger logger) {
        this.server = server;
        this.toggles = toggles;
        this.settings = settings;
        this.logger = logger;
    }

    @Override
    public void run() {
        if (!settings.get(CoreSettings.GUI_STATUS_LINE)) {
            return;
        }
        List<StatusToggle> all = toggles.toggles();
        for (Player player : server.getOnlinePlayers()) {
            if (toggles.statusLinePaused(player.getUniqueId())) {
                continue;
            }
            List<Component> on = new ArrayList<>();
            for (StatusToggle toggle : all) {
                if (shows(toggle, player)) {
                    on.add(toggle.label(player).colorIfAbsent(NamedTextColor.GOLD));
                }
            }
            if (!on.isEmpty()) {
                player.sendActionBar(Component.join(JoinConfiguration.separator(SEPARATOR), on));
            }
        }
    }

    private boolean shows(StatusToggle toggle, Player player) {
        try {
            return (toggle.permission() == null || player.hasPermission(toggle.permission().value())) && toggle.active(player);
        } catch (RuntimeException failure) {
            // One extension's toggle must not take the line away for everyone.
            logger.log(Level.FINE, "Status toggle " + toggle.id() + " failed", failure);
            return false;
        }
    }
}

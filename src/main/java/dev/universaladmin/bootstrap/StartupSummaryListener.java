package dev.universaladmin.bootstrap;

import dev.universaladmin.core.PluginStatus;
import dev.universaladmin.core.UniversalAdmin;
import dev.universaladmin.localization.ComponentMessages;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.localization.MessageService;
import dev.universaladmin.module.ModuleId;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerLoadEvent;

/**
 * Prints the colored, multi-line startup summary once the whole server -
 * not just this plugin - has finished starting. {@link ServerLoadEvent}
 * fires after every plugin's {@code onEnable} and all worlds are loaded
 * (for both {@code LoadType.STARTUP} and a global {@code /reload}), which is
 * the actual "the server is done starting" signal Bukkit provides - unlike
 * scraping the console for the "Done!" line, this can't drift out of sync
 * with what it's trying to detect.
 */
final class StartupSummaryListener implements Listener {

    private final UniversalAdmin platform;

    StartupSummaryListener(UniversalAdmin platform) {
        this.platform = platform;
    }

    @EventHandler
    public void onServerLoad(ServerLoadEvent event) {
        MessageService messages = platform.messages();
        PluginStatus status = platform.status();

        for (Component line : bannerLines(messages, status)) {
            Bukkit.getConsoleSender().sendMessage(line);
        }
    }

    private List<Component> bannerLines(MessageService messages, PluginStatus status) {
        Component separator = ComponentMessages.render(messages.get(MessageKey.of("plugin.startup-banner.separator")));

        List<Component> lines = new ArrayList<>();
        lines.add(separator);
        lines.add(ComponentMessages.render(messages.get(MessageKey.of("plugin.startup-banner.header"), status.version())));
        lines.add(ComponentMessages.render(messages.get(
                MessageKey.of("plugin.startup-banner.modules"), status.activeModules().size(), status.totalModules())));
        if (!status.failedModules().isEmpty()) {
            lines.add(ComponentMessages.render(messages.get(
                    MessageKey.of("plugin.startup-banner.modules-failed"), status.failedModules().size(), joinIds(status.failedModules()))));
        }
        lines.add(ComponentMessages.render(messages.get(MessageKey.of("plugin.startup-banner.database"), status.database())));
        lines.add(separator);
        return lines;
    }

    private String joinIds(List<ModuleId> ids) {
        return ids.stream().map(ModuleId::toString).collect(Collectors.joining(", "));
    }
}

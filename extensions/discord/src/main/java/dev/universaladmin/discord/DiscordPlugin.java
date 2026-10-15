package dev.universaladmin.discord;

import dev.universaladmin.core.UniversalAdmin;
import dev.universaladmin.core.UniversalAdminProvider;
import dev.universaladmin.localization.MessageBundles;
import java.util.Optional;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * UniversalAdmin Discord: loads its config, then registers {@link DiscordModule}
 * with UniversalAdmin - which owns its command, permission and audit
 * subscription from then on.
 */
public final class DiscordPlugin extends JavaPlugin {

    private volatile DiscordConfig config;
    private WebhookClient client;

    @Override
    public void onEnable() {
        Optional<UniversalAdmin> universalAdmin = UniversalAdminProvider.find();
        if (universalAdmin.isEmpty()) {
            getLogger().severe("UniversalAdmin is not running - UniversalAdminDiscord needs it and disables itself.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        saveDefaultConfig();
        loadSettings();
        client = new WebhookClient(getLogger());
        MessageBundles.register(this, universalAdmin.get().messages(), "en_US", "de_DE");
        universalAdmin.get().extensions().register(this, new DiscordModule(() -> config, this::loadSettings, client));
    }

    @Override
    public void onDisable() {
        if (client != null) {
            client.close();
        }
    }

    private void loadSettings() {
        reloadConfig();
        config = DiscordConfig.from(getConfig());
    }
}

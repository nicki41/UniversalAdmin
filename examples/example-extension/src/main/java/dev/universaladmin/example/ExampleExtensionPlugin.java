package dev.universaladmin.example;

import dev.universaladmin.core.UniversalAdmin;
import dev.universaladmin.core.UniversalAdminProvider;
import dev.universaladmin.localization.MessageBundles;
import java.util.Optional;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * The plugin side of the extension: all it does is hand its module to
 * UniversalAdmin. Everything else - commands, GUI, actions, permissions,
 * settings - lives in {@link ExampleModule} and is cleaned up by
 * UniversalAdmin automatically when this plugin is disabled.
 */
public final class ExampleExtensionPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        Optional<UniversalAdmin> universalAdmin = UniversalAdminProvider.find();
        if (universalAdmin.isEmpty()) {
            getLogger().severe("UniversalAdmin is not running - disabling the example extension.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        // Our own translations; server owners can override every key in
        // plugins/UniversalAdmin/lang/<locale>.yml.
        MessageBundles.register(this, universalAdmin.get().messages(), "en_US", "de_DE");
        universalAdmin.get().extensions().register(this, new ExampleModule());
    }
}

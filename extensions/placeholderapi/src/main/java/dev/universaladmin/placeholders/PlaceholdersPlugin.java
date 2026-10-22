package dev.universaladmin.placeholders;

import dev.universaladmin.core.UniversalAdmin;
import dev.universaladmin.core.UniversalAdminProvider;
import dev.universaladmin.localization.MessageBundles;
import java.util.Optional;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * UniversalAdmin Placeholders: registers {@link PlaceholdersModule} with
 * UniversalAdmin, which registers the PlaceholderAPI expansion. Needs
 * UniversalAdmin 1.2 or newer (the status services of API 1.2).
 */
public final class PlaceholdersPlugin extends JavaPlugin {

    private static final String REQUIRED_API = "1.3";

    @Override
    public void onEnable() {
        Optional<UniversalAdmin> universalAdmin = UniversalAdminProvider.find();
        if (universalAdmin.isEmpty()) {
            getLogger().severe("UniversalAdmin is not running - UniversalAdminPlaceholders needs it and disables itself.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        if (compare(UniversalAdminProvider.API_VERSION, REQUIRED_API) < 0) {
            getLogger().severe("UniversalAdminPlaceholders needs UniversalAdmin " + REQUIRED_API + " or newer - please update it.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        MessageBundles.register(this, universalAdmin.get().messages(), "en_US", "de_DE");
        universalAdmin.get().extensions().register(this,
                new PlaceholdersModule(new UniversalAdminExpansion(this, universalAdmin.get())));
    }

    /** Compares "major.minor" version strings numerically. */
    static int compare(String version, String required) {
        String[] have = version.split("\\.");
        String[] need = required.split("\\.");
        for (int i = 0; i < Math.max(have.length, need.length); i++) {
            int a = i < have.length ? Integer.parseInt(have[i]) : 0;
            int b = i < need.length ? Integer.parseInt(need[i]) : 0;
            if (a != b) {
                return Integer.compare(a, b);
            }
        }
        return 0;
    }
}

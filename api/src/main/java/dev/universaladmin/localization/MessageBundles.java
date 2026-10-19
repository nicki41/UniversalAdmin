package dev.universaladmin.localization;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

/**
 * Loads an extension's own translations into the shared
 * {@link MessageService}: each {@code lang/<locale>.yml} inside the
 * extension's jar becomes a bundled-defaults layer, which server owners can
 * still override in UniversalAdmin's own {@code lang/<locale>.yml}.
 *
 * <pre>{@code
 * MessageBundles.register(myPlugin, context.platform().messages(), "en_US", "de_DE");
 * }</pre>
 *
 * Keys should start with the extension's own namespace
 * ({@code myextension.gui.title}) so they never collide with core keys.
 */
public final class MessageBundles {

    private MessageBundles() {
    }

    /** Registers {@code lang/<locale>.yml} from {@code plugin}'s jar for each locale; a missing file is logged and skipped. */
    public static void register(Plugin plugin, MessageService messages, String... locales) {
        for (String locale : locales) {
            String resource = "lang/" + locale + ".yml";
            try (InputStream in = plugin.getResource(resource)) {
                if (in == null) {
                    plugin.getLogger().warning("Translation file " + resource + " not found in " + plugin.getName() + "'s jar");
                    continue;
                }
                messages.registerDefaults(locale, read(new String(in.readAllBytes(), StandardCharsets.UTF_8)));
            } catch (IOException | InvalidConfigurationException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not load translation file " + resource, e);
            }
        }
    }

    /** Flattens a YAML document into dotted keys - {@code a: {b: "x"}} becomes {@code a.b = x}. */
    public static Map<String, String> read(String yaml) throws InvalidConfigurationException {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString(yaml);
        Map<String, String> flat = new LinkedHashMap<>();
        for (String key : config.getKeys(true)) {
            if (config.isString(key)) {
                flat.put(key, config.getString(key));
            }
        }
        return flat;
    }
}

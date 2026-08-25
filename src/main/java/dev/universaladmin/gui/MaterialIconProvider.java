package dev.universaladmin.gui;

import dev.universaladmin.module.GuiIcon;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import org.bukkit.Material;

/**
 * Default {@link IconProvider}: resolves {@link GuiIcon#materialKey()} via
 * {@link Material#matchMaterial(String)}. An unresolvable key never breaks
 * a menu - it falls back to {@link Material#PAPER} and logs a warning once
 * per key, the same tolerant-fallback approach {@code YamlSettingsService}
 * uses for a bad config value (see docs/development/settings.md) - a typo
 * in a module's icon key is not worth failing the whole GUI over.
 */
public final class MaterialIconProvider implements IconProvider {

    private final Logger logger;
    private final Set<String> warnedUnknownKeys = ConcurrentHashMap.newKeySet();

    public MaterialIconProvider(Logger logger) {
        this.logger = logger;
    }

    @Override
    public Material resolve(GuiIcon icon) {
        Material material = Material.matchMaterial(icon.materialKey());
        if (material != null) {
            return material;
        }
        if (warnedUnknownKeys.add(icon.materialKey())) {
            logger.warning(() -> "Unknown material key '" + icon.materialKey() + "' for GUI icon '"
                    + icon.label() + "' - falling back to PAPER.");
        }
        return Material.PAPER;
    }
}

package dev.universaladmin.module;

/**
 * Placeholder for how a module presents itself in a future menu/dashboard.
 * No GUI framework decision has been made yet (see docs/architecture/gui.md)
 * and no built-in module sets one today - this exists purely so
 * {@link ModuleDescriptor} has a stable place for that metadata to live once
 * there is a menu to render it, without another round of interface changes.
 *
 * <p>Deliberately Bukkit-independent (a plain material key string, not
 * {@code org.bukkit.Material}) so the same descriptor could feed an in-game
 * menu or a future web dashboard widget alike.
 */
public record GuiIcon(String materialKey, String label) {
}

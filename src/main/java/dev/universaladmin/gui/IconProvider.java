package dev.universaladmin.gui;

import dev.universaladmin.module.GuiIcon;
import org.bukkit.Material;

/**
 * The one place a {@link Material} gets chosen from a module- or
 * framework-level icon concept - see the "Icons" section of
 * docs/development/gui-framework.md. No {@code GuiPage} implementation
 * should reference {@link Material} directly for a module icon or a
 * standard framework control; it asks an {@code IconProvider} instead, so
 * a future resource-pack-aware provider (custom model data, not just a
 * vanilla {@code Material}) can replace {@link MaterialIconProvider}
 * without touching a single page.
 *
 * <p>The default methods are the small, fixed set of icons the framework
 * itself needs for chrome that every page shares (back/close/refresh,
 * pagination, loading/empty/error placeholders) - a module never defines
 * its own version of these.
 */
public interface IconProvider {

    /** Resolves a module- or feature-declared {@link GuiIcon} to a concrete {@link Material}. */
    Material resolve(GuiIcon icon);

    default Material back() {
        return Material.ARROW;
    }

    default Material close() {
        return Material.BARRIER;
    }

    default Material refresh() {
        return Material.CLOCK;
    }

    default Material previousPage() {
        return Material.ARROW;
    }

    default Material nextPage() {
        return Material.ARROW;
    }

    default Material pageIndicator() {
        return Material.PAPER;
    }

    default Material loading() {
        return Material.YELLOW_STAINED_GLASS_PANE;
    }

    default Material empty() {
        return Material.GRAY_STAINED_GLASS_PANE;
    }

    default Material error() {
        return Material.RED_STAINED_GLASS_PANE;
    }

    default Material confirm(ConfirmationDialog.DangerLevel dangerLevel) {
        return switch (dangerLevel) {
            case NORMAL -> Material.LIME_WOOL;
            case WARNING -> Material.YELLOW_WOOL;
            case DANGEROUS -> Material.RED_WOOL;
        };
    }

    default Material cancel() {
        return Material.BARRIER;
    }
}

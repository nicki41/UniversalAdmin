package dev.universaladmin.gui;

import org.bukkit.entity.Player;

/**
 * Passed to {@link AbstractGuiPage#renderContent}. {@code viewer} is only
 * valid for the duration of that call (it comes from the live
 * {@link GuiPage#open} invocation, itself always on the main thread) -
 * a subclass that needs it later, after an async load completes, gets a
 * fresh one from {@link AbstractListGuiPage}'s own main-thread callback
 * rather than closing over this record. See docs/development/gui-framework.md.
 */
public record GuiRenderContext(GuiView view, GuiSession session, Player viewer) {
}

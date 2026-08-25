package dev.universaladmin.gui;

/**
 * The small bundle of framework-internal machinery every {@link AbstractGuiPage}
 * needs, threaded through its constructor like any other explicit dependency
 * (see docs/architecture/gui.md) - not a substitute for the
 * {@code UniversalAdmin platform} anti-pattern that ADR-0004 already rejects,
 * just the GUI-framework-scoped equivalent of {@link dev.universaladmin.action.ActionContext}
 * or {@link dev.universaladmin.module.ModuleContext}. A page still declares
 * its own domain services (a {@code PlayerService}, ...) as separate
 * constructor parameters.
 *
 * <p>One instance is built once in {@code UniversalAdminPlugin#bootstrapCore}
 * and lives on {@link dev.universaladmin.core.UniversalAdmin#guiFramework()}.
 */
public record GuiFramework(GuiSessionManager sessions, IconProvider icons) {
}

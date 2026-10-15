package dev.universaladmin.gui;

import dev.universaladmin.localization.ComponentMessages;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.localization.MessageService;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/**
 * Shared base for every UniversalAdmin GUI page: renders the standard
 * {@link GuiLayout} navigation row (back/refresh/close) and leaves the
 * content area to {@link #renderContent}. See docs/development/gui-framework.md
 * for a worked example of extending this.
 *
 * <p>A subclass gets its own domain dependencies (a {@code PlayerService},
 * ...) through its constructor in addition to {@link GuiFramework}/
 * {@link MessageService} - the same explicit-dependency-injection shape
 * every {@code GuiPage} already followed before this framework existed
 * (see docs/architecture/gui.md), now just with a common base class instead
 * of each page hand-rolling inventory setup.
 */
public abstract class AbstractGuiPage implements GuiPage {

    protected final GuiPageId id;
    protected final GuiFramework framework;
    protected final MessageService messages;
    private final int rows;

    protected AbstractGuiPage(GuiPageId id, GuiFramework framework, MessageService messages) {
        this(id, framework, messages, GuiLayout.ROWS);
    }

    protected AbstractGuiPage(GuiPageId id, GuiFramework framework, MessageService messages, int rows) {
        this.id = id;
        this.framework = framework;
        this.messages = messages;
        this.rows = rows;
    }

    @Override
    public final GuiPageId id() {
        return id;
    }

    /** The inventory title shown to {@code viewer} - always through {@link #messages}, never a literal string. */
    protected abstract Component title(Player viewer);

    /**
     * Fills {@link GuiLayout}'s content area (and, if needed, the pagination
     * slots in the bottom row) for {@code context}. Called once per
     * {@link #open}, i.e. once per navigation/back/refresh - a page that
     * loads data asynchronously renders a loading placeholder here and
     * updates the view later from a main-thread callback; see
     * {@link AbstractListGuiPage} for the reusable version of that pattern.
     */
    protected abstract void renderContent(GuiRenderContext context);

    /** Whether the refresh control is shown - {@code true} by default. */
    protected boolean refreshable() {
        return true;
    }

    /**
     * Builds a fresh {@link GuiView}, renders chrome and content into it,
     * and opens it for {@code viewer}. Also how "back", "refresh", and
     * "open a sub-page" all work - see {@link GuiClickContext}.
     */
    @Override
    public final void open(Player viewer) {
        GuiSession session = framework.sessions().sessionFor(viewer.getUniqueId());
        GuiView view = new GuiView(this, viewer.getUniqueId(), rows, title(viewer));
        renderChrome(view, session, viewer);
        renderContent(new GuiRenderContext(view, session, viewer));
        viewer.openInventory(view.getInventory());
    }

    private void renderChrome(GuiView view, GuiSession session, Player viewer) {
        if (session.hasHistory()) {
            view.place(GuiLayout.BACK_SLOT,
                    GuiButton.of(GuiItem.of(framework.icons().back(), text("gui.back")), GuiClickContext::back),
                    viewer);
        }
        if (refreshable()) {
            view.place(GuiLayout.REFRESH_SLOT,
                    GuiButton.of(GuiItem.of(framework.icons().refresh(), text("gui.refresh")),
                            ctx -> this.open(ctx.viewer())),
                    viewer);
        }
        view.place(GuiLayout.CLOSE_SLOT,
                GuiButton.of(GuiItem.of(framework.icons().close(), text("gui.close")), GuiClickContext::close),
                viewer);
    }

    protected final Component text(String messageKey, Object... args) {
        return ComponentMessages.render(messages.get(MessageKey.of(messageKey), args));
    }
}

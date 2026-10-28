package dev.universaladmin.gui;

import dev.universaladmin.localization.ComponentMessages;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.localization.MessageService;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;

/**
 * Shared base for every UniversalAdmin GUI page: draws the frame and leaves
 * the content area to {@link #renderContent} (ADR 0027, and
 * docs/development/gui-framework.md for a worked example).
 *
 * <ul>
 *   <li>The header row in the accent of the page's module: back, start (the
 *       main menu), the page's own icon, refresh, close.</li>
 *   <li>Every slot the page leaves empty becomes a black pane - unused space
 *       never looks like a slot to put something in.</li>
 *   <li>Moving to another page of the same size redraws the open window
 *       instead of opening a new inventory - no flicker, the mouse stays.</li>
 * </ul>
 *
 * <p>A subclass gets its own domain dependencies (a {@code PlayerService},
 * ...) through its constructor in addition to {@link GuiFramework}/
 * {@link MessageService} - the explicit-dependency-injection shape of
 * docs/architecture/gui.md.
 */
public abstract class AbstractGuiPage implements GuiPage {

    private static final LegacyComponentSerializer TITLE = LegacyComponentSerializer.builder()
            .character(LegacyComponentSerializer.SECTION_CHAR).hexColors().useUnusualXRepeatedCharacterHexFormat().build();

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
     * Fills {@link GuiLayout}'s content area (and, if needed, the toolbar
     * row) for {@code context}. Called once per {@link #open}, i.e. once per
     * navigation/back/refresh - a page that loads data asynchronously renders
     * a loading placeholder here and updates the view later from a
     * main-thread callback; see {@link AbstractListGuiPage} for the reusable
     * version of that pattern.
     */
    protected abstract void renderContent(GuiRenderContext context);

    /** Whether the refresh control is shown - {@code true} by default. */
    protected boolean refreshable() {
        return true;
    }

    /**
     * The colour of the header row - the accent of the page's module by
     * default ({@link GuiStyle#accent}). A dialog uses its danger level.
     *
     * @since 1.7
     */
    protected GuiGlass accent() {
        return framework.style().accent(id);
    }

    /**
     * The item in the middle of the header: the page's own icon with its
     * title. A page whose module has an icon shows that one by default.
     *
     * @since 1.7
     */
    protected GuiItem header(Player viewer) {
        return GuiItem.of(framework.style().icon(id), title(viewer));
    }

    /**
     * Draws this page for {@code viewer} - into the window they have open
     * when it is one of ours of the same size (the inventory editors excepted,
     * whose close handling needs a real close), otherwise into a new one.
     * Also how "back", "refresh", and "open a sub-page" all work - see
     * {@link GuiClickContext}.
     */
    @Override
    public final void open(Player viewer) {
        GuiSession session = framework.sessions().sessionFor(viewer.getUniqueId());
        Component title = title(viewer);
        Optional<GuiWindow> reusable = reusableWindow(viewer);
        GuiWindow window = reusable.orElseGet(() -> new GuiWindow(viewer.getUniqueId(), rows, title));
        window.getInventory().clear();
        GuiView view = new GuiView(this, window);
        window.current(view);
        renderChrome(view, session, viewer);
        renderContent(new GuiRenderContext(view, session, viewer));
        view.fillEmpty();
        if (reusable.isEmpty()) {
            viewer.openInventory(window.getInventory());
        } else if (!title.equals(window.title())) {
            viewer.getOpenInventory().setTitle(TITLE.serialize(title));
        }
        window.title(title);
    }

    private Optional<GuiWindow> reusableWindow(Player viewer) {
        if (!(viewer.getOpenInventory().getTopInventory().getHolder() instanceof GuiWindow window)) {
            return Optional.empty();
        }
        GuiView shown = window.current();
        boolean fits = window.rows() == rows && window.viewerId().equals(viewer.getUniqueId());
        boolean plainMenu = shown != null && !shown.editable() && !shown.hasCloseCallback();
        return fits && plainMenu ? Optional.of(window) : Optional.empty();
    }

    private void renderChrome(GuiView view, GuiSession session, Player viewer) {
        GuiItem accent = GuiItem.pane(accent());
        for (int slot = 0; slot < GuiLayout.COLUMNS; slot++) {
            view.place(slot, accent);
        }
        if (session.hasHistory()) {
            view.place(GuiLayout.BACK_SLOT,
                    GuiButton.of(GuiItem.of(framework.icons().back(), text("gui.back")), GuiClickContext::back),
                    viewer);
        }
        framework.style().home()
                .filter(home -> !home.id().equals(id))
                .ifPresent(home -> view.place(GuiLayout.HOME_SLOT,
                        GuiButton.of(GuiItem.of(framework.icons().home(), text("gui.home")), ctx -> {
                            ctx.session().clearHistory();
                            home.open(ctx.viewer());
                        }),
                        viewer));
        view.place(GuiLayout.HEADER_SLOT, header(viewer));
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

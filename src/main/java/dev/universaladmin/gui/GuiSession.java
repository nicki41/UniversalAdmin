package dev.universaladmin.gui;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player GUI state, owned by {@link GuiSessionManager}. Keyed and
 * identified by {@link UUID}, never by a {@link org.bukkit.entity.Player}
 * reference - see the "Player Session" section of
 * docs/development/gui-framework.md for why holding a live {@code Player}
 * here would be a leak risk.
 *
 * <p>A registered {@link AbstractGuiPage} is a singleton shared by every
 * viewer (like any other framework/service object in this codebase - see
 * docs/architecture/gui.md), so it must never hold per-player state as
 * instance fields. This class is where that state goes instead: the
 * navigation history (for {@link GuiClickContext#back()}) and a small
 * attribute bag a page uses for things like "which pagination page am I
 * on" - keyed by that page's own {@link GuiPageId} so unrelated pages can't
 * collide.
 */
public final class GuiSession {

    private final UUID playerId;
    private final Deque<Runnable> history = new ArrayDeque<>();
    private final Map<String, Object> attributes = new ConcurrentHashMap<>();

    GuiSession(UUID playerId) {
        this.playerId = playerId;
    }

    public UUID playerId() {
        return playerId;
    }

    public void setAttribute(String key, Object value) {
        attributes.put(key, value);
    }

    public Optional<Object> attribute(String key) {
        return Optional.ofNullable(attributes.get(key));
    }

    /** Removes a single attribute - the "no filter on this dimension" case, since a {@link ConcurrentHashMap} value can't be {@code null}. */
    public void removeAttribute(String key) {
        attributes.remove(key);
    }

    /** Convenience for the common "current page number" style of attribute. */
    public int intAttribute(String key, int defaultValue) {
        return attribute(key)
                .filter(Integer.class::isInstance)
                .map(Integer.class::cast)
                .orElse(defaultValue);
    }

    /** Clears every attribute - not navigation history. Called when a fresh top-level page is opened. */
    public void clearAttributes() {
        attributes.clear();
    }

    /** How to redraw the page being navigated away from; popped by {@link GuiClickContext#back()}. */
    void pushHistory(Runnable reopenPreviousPage) {
        history.push(reopenPreviousPage);
    }

    boolean hasHistory() {
        return !history.isEmpty();
    }

    Optional<Runnable> popHistory() {
        return history.isEmpty() ? Optional.empty() : Optional.of(history.pop());
    }

    void clearHistory() {
        history.clear();
    }
}

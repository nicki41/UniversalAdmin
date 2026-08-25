package dev.universaladmin.gui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

/**
 * The whole navigation model in one place - see the "Navigation" section of
 * docs/development/gui-framework.md: opening a page is always
 * {@link GuiPage#open}, forward navigation records how to redraw the page
 * being left, and "back" replays that recording. {@link GuiPage}/
 * {@link GuiView} are mocked (both are safe to mock - a mocked
 * {@code GuiPage} never touches Bukkit inventory APIs, and only
 * {@link GuiView#page()} is ever called here), so this test needs no live
 * inventory - see docs/development/testing.md.
 */
class GuiClickContextTest {

    private final Player viewer = mock(Player.class);
    private final GuiSession session = new GuiSession(UUID.randomUUID());

    @Test
    void openPushesAReopenerForTheCurrentPageThenOpensNext() {
        GuiPage current = mock(GuiPage.class);
        GuiView view = mock(GuiView.class);
        when(view.page()).thenReturn(current);
        GuiPage next = mock(GuiPage.class);

        new GuiClickContext(viewer, GuiClickType.LEFT, session, view).open(next);

        verify(next).open(viewer);
        assertTrue(session.hasHistory());
        verify(current, never()).open(viewer);
    }

    @Test
    void backReplaysWhicheverPageWasNavigatedAwayFrom() {
        GuiPage current = mock(GuiPage.class);
        GuiView view = mock(GuiView.class);
        when(view.page()).thenReturn(current);
        GuiPage next = mock(GuiPage.class);
        new GuiClickContext(viewer, GuiClickType.LEFT, session, view).open(next);

        // A click on the now-open "next" page asks to go back.
        GuiView nextView = mock(GuiView.class);
        new GuiClickContext(viewer, GuiClickType.RIGHT, session, nextView).back();

        verify(current).open(viewer);
        assertFalse(session.hasHistory(), "back() must consume the history entry it replayed");
    }

    @Test
    void backClosesTheInventoryWhenThereIsNoHistoryToReplay() {
        GuiView view = mock(GuiView.class);

        new GuiClickContext(viewer, GuiClickType.LEFT, session, view).back();

        verify(viewer).closeInventory();
    }

    @Test
    void closeAlwaysClosesTheInventory() {
        GuiView view = mock(GuiView.class);

        new GuiClickContext(viewer, GuiClickType.LEFT, session, view).close();

        verify(viewer).closeInventory();
    }

    @Test
    void multipleForwardNavigationsBuildAMultiStepHistory() {
        GuiPage pageA = mock(GuiPage.class);
        GuiView viewA = mock(GuiView.class);
        when(viewA.page()).thenReturn(pageA);
        GuiPage pageB = mock(GuiPage.class);
        GuiView viewB = mock(GuiView.class);
        when(viewB.page()).thenReturn(pageB);
        GuiPage pageC = mock(GuiPage.class);

        new GuiClickContext(viewer, GuiClickType.LEFT, session, viewA).open(pageB);
        new GuiClickContext(viewer, GuiClickType.LEFT, session, viewB).open(pageC);
        // pageB.open() has now run once already, as part of navigating to it above.

        // Two steps deep - back() must return to B first, not straight to A.
        new GuiClickContext(viewer, GuiClickType.LEFT, session, mock(GuiView.class)).back();
        verify(pageB, times(2)).open(viewer);
        verify(pageA, never()).open(viewer);

        new GuiClickContext(viewer, GuiClickType.LEFT, session, mock(GuiView.class)).back();
        verify(pageA).open(viewer);
    }
}

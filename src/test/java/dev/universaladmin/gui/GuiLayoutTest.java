package dev.universaladmin.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The one source of truth for slot numbers - see the "Slots" section of
 * docs/development/gui-framework.md. Pure arithmetic, no Bukkit needed.
 */
class GuiLayoutTest {

    @Test
    void contentSlotMapsIndexZeroToTheFirstContentSlot() {
        assertEquals(GuiLayout.CONTENT_START_SLOT, GuiLayout.contentSlot(0));
    }

    @Test
    void contentSlotMapsTheLastIndexToTheLastContentSlot() {
        assertEquals(GuiLayout.CONTENT_END_SLOT, GuiLayout.contentSlot(GuiLayout.contentSize() - 1));
    }

    @Test
    void contentSizeIsExactlyTheFourMiddleRows() {
        assertEquals(36, GuiLayout.contentSize());
    }

    @Test
    void contentSlotRejectsAnOutOfRangeIndex() {
        assertThrows(IndexOutOfBoundsException.class, () -> GuiLayout.contentSlot(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> GuiLayout.contentSlot(GuiLayout.contentSize()));
    }

    @Test
    void chromeSlotsDoNotOverlapTheContentArea() {
        for (int slot : new int[] {GuiLayout.BACK_SLOT, GuiLayout.REFRESH_SLOT, GuiLayout.CLOSE_SLOT}) {
            assertTrue(slot < GuiLayout.CONTENT_START_SLOT, "nav slot " + slot + " must be above the content area");
        }
        for (int slot : new int[] {GuiLayout.PREVIOUS_PAGE_SLOT, GuiLayout.PAGE_INDICATOR_SLOT, GuiLayout.NEXT_PAGE_SLOT}) {
            assertTrue(slot > GuiLayout.CONTENT_END_SLOT, "pagination slot " + slot + " must be below the content area");
        }
    }

    @Test
    void sizeIsRowsTimesColumns() {
        assertEquals(GuiLayout.ROWS * GuiLayout.COLUMNS, GuiLayout.SIZE);
    }
}

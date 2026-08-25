package dev.universaladmin.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Pure logic, no Bukkit - see docs/development/testing.md. Exercises the
 * generic slicing/clamping {@link AbstractListGuiPage} builds on.
 */
class PaginationTest {

    @Test
    void slicesItemsIntoPagesOfTheRequestedSize() {
        Pagination<Integer> pagination = new Pagination<>(List.of(1, 2, 3, 4, 5), 2, 0);

        assertEquals(List.of(1, 2), pagination.currentPageItems());
        assertEquals(2, pagination.maxPage());
    }

    @Test
    void lastPageHoldsTheRemainder() {
        Pagination<Integer> pagination = new Pagination<>(List.of(1, 2, 3, 4, 5), 2, 2);

        assertEquals(List.of(5), pagination.currentPageItems());
        assertFalse(pagination.hasNext());
    }

    @Test
    void emptyListIsASingleEmptyPage() {
        Pagination<Integer> pagination = new Pagination<>(List.of(), 10, 0);

        assertEquals(List.of(), pagination.currentPageItems());
        assertEquals(0, pagination.maxPage());
        assertFalse(pagination.hasPrevious());
        assertFalse(pagination.hasNext());
    }

    @Test
    void clampedPullsAnOutOfRangePageBackIntoBounds() {
        Pagination<Integer> tooHigh = new Pagination<>(List.of(1, 2, 3), 2, 99);
        assertEquals(1, tooHigh.clamped().currentPage());

        Pagination<Integer> negative = new Pagination<>(List.of(1, 2, 3), 2, -5);
        assertEquals(0, negative.clamped().currentPage());
    }

    @Test
    void hasPreviousAndHasNextReflectPosition() {
        Pagination<Integer> middle = new Pagination<>(List.of(1, 2, 3, 4, 5, 6), 2, 1);

        assertTrue(middle.hasPrevious());
        assertTrue(middle.hasNext());
        assertEquals(List.of(3, 4), middle.currentPageItems());
    }

    @Test
    void previousPageAndNextPageStayClamped() {
        Pagination<Integer> firstPage = new Pagination<>(List.of(1, 2, 3), 2, 0);
        assertEquals(0, firstPage.previousPage().currentPage());

        Pagination<Integer> lastPage = new Pagination<>(List.of(1, 2, 3), 2, 1);
        assertEquals(1, lastPage.nextPage().currentPage());
    }

    @Test
    void displayPageAndDisplayMaxPageAreOneBased() {
        Pagination<Integer> pagination = new Pagination<>(List.of(1, 2, 3, 4, 5), 2, 1);

        assertEquals(2, pagination.displayPage());
        assertEquals(3, pagination.displayMaxPage());
    }

    @Test
    void rejectsANonPositivePageSize() {
        assertThrows(IllegalArgumentException.class, () -> new Pagination<>(List.of(1), 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new Pagination<>(List.of(1), -1, 0));
    }
}

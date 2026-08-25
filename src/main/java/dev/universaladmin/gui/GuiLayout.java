package dev.universaladmin.gui;

/**
 * The one shared slot layout every {@link AbstractGuiPage} renders into - a
 * fixed 6-row chest: top row for navigation, four middle rows as the content
 * area, bottom row for pagination/bulk controls. No feature page picks its
 * own slot numbers; everything goes through this class, so "where does the
 * back button live" has exactly one answer across the whole plugin.
 */
public final class GuiLayout {

    public static final int ROWS = 6;
    public static final int COLUMNS = 9;
    public static final int SIZE = ROWS * COLUMNS;

    /** Row 0: back (left), refresh (center), close (right). */
    public static final int BACK_SLOT = 0;
    public static final int REFRESH_SLOT = 4;
    public static final int CLOSE_SLOT = 8;

    /** Rows 1-4: the only slots {@link AbstractGuiPage} subclasses render feature content into. */
    public static final int CONTENT_START_SLOT = 9;
    public static final int CONTENT_END_SLOT = 44;

    /** Row 5: pagination controls. */
    public static final int PREVIOUS_PAGE_SLOT = 48;
    public static final int PAGE_INDICATOR_SLOT = 49;
    public static final int NEXT_PAGE_SLOT = 50;

    private GuiLayout() {
    }

    /** Number of slots in the content area - the default page size for a paginated page. */
    public static int contentSize() {
        return CONTENT_END_SLOT - CONTENT_START_SLOT + 1;
    }

    /** Maps a zero-based content index ({@code 0..contentSize()-1}) to its absolute inventory slot. */
    public static int contentSlot(int index) {
        if (index < 0 || index >= contentSize()) {
            throw new IndexOutOfBoundsException(
                    "Content index " + index + " out of range [0, " + contentSize() + ")");
        }
        return CONTENT_START_SLOT + index;
    }
}

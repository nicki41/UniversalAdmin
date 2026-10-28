package dev.universaladmin.gui;

/**
 * The one shared slot layout every {@link AbstractGuiPage} renders into - a
 * fixed 6-row chest: the header row (navigation, drawn in the module's
 * colour), four middle rows as the content area, and the toolbar row
 * (tabs and filters left, paging in the middle, sort and search right). No
 * feature page picks its own slot numbers; everything goes through this
 * class, so "where does the back button live" has exactly one answer across
 * the whole plugin. See ADR 0027.
 */
public final class GuiLayout {

    public static final int ROWS = 6;
    public static final int COLUMNS = 9;
    public static final int SIZE = ROWS * COLUMNS;

    /** Row 0, the header: back, start (the main menu), the page's own icon in the middle, refresh, close. */
    public static final int BACK_SLOT = 0;
    /** @since 1.7 */
    public static final int HOME_SLOT = 1;
    /** @since 1.7 */
    public static final int HEADER_SLOT = 4;
    /** In the middle until 1.7, next to close since. */
    public static final int REFRESH_SLOT = 7;
    public static final int CLOSE_SLOT = 8;

    /** Rows 1-4: the only slots {@link AbstractGuiPage} subclasses render feature content into. */
    public static final int CONTENT_START_SLOT = 9;
    public static final int CONTENT_END_SLOT = 44;

    /** Row 5, the toolbar: paging in the middle. */
    public static final int PREVIOUS_PAGE_SLOT = 48;
    public static final int PAGE_INDICATOR_SLOT = 49;
    public static final int NEXT_PAGE_SLOT = 50;

    /**
     * Row 5, the toolbar around the paging controls: tabs and filters on the
     * left ({@code TOOL_LEFT_*}), sort, search and page actions on the right
     * ({@code TOOL_RIGHT_*}, search last).
     *
     * @since 1.7
     */
    public static final int TOOL_LEFT_1 = 45;
    /** @since 1.7 */
    public static final int TOOL_LEFT_2 = 46;
    /** @since 1.7 */
    public static final int TOOL_LEFT_3 = 47;
    /** @since 1.7 */
    public static final int TOOL_RIGHT_1 = 51;
    /** @since 1.7 */
    public static final int TOOL_RIGHT_2 = 52;
    /** @since 1.7 */
    public static final int TOOL_RIGHT_3 = 53;

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

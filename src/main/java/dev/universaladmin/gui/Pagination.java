package dev.universaladmin.gui;

import java.util.List;

/**
 * Pure pagination logic over an already-loaded list - no Bukkit, no IO, so
 * it's testable without a server (see docs/development/testing.md). A
 * {@link AbstractListGuiPage} is the framework's only caller, but the type
 * is public because a module's own page may need the same slicing logic
 * outside that base class.
 *
 * @param allItems    the full, unpaginated list
 * @param pageSize    items per page; must be positive
 * @param currentPage zero-based page index, clamped into {@code [0, maxPage()]} by {@link #clamped()}
 */
public record Pagination<T>(List<T> allItems, int pageSize, int currentPage) {

    public Pagination {
        if (pageSize <= 0) {
            throw new IllegalArgumentException("pageSize must be positive, was " + pageSize);
        }
        allItems = List.copyOf(allItems);
    }

    /** Highest valid zero-based page index; {@code 0} for an empty list (a single, empty page). */
    public int maxPage() {
        return allItems.isEmpty() ? 0 : (allItems.size() - 1) / pageSize;
    }

    /** Same pagination with {@link #currentPage()} clamped into {@code [0, maxPage()]}. */
    public Pagination<T> clamped() {
        int clamped = Math.max(0, Math.min(currentPage, maxPage()));
        return clamped == currentPage ? this : new Pagination<>(allItems, pageSize, clamped);
    }

    /** The slice of {@link #allItems()} for {@link #currentPage()} - empty if {@link #allItems()} is empty. */
    public List<T> currentPageItems() {
        if (allItems.isEmpty()) {
            return List.of();
        }
        int from = Math.min(currentPage * pageSize, allItems.size());
        int to = Math.min(from + pageSize, allItems.size());
        return allItems.subList(from, to);
    }

    public boolean hasPrevious() {
        return currentPage > 0;
    }

    public boolean hasNext() {
        return currentPage < maxPage();
    }

    public Pagination<T> withPage(int page) {
        return new Pagination<>(allItems, pageSize, page).clamped();
    }

    public Pagination<T> previousPage() {
        return withPage(currentPage - 1);
    }

    public Pagination<T> nextPage() {
        return withPage(currentPage + 1);
    }

    /** 1-based page number for display, e.g. "Page 1/4". */
    public int displayPage() {
        return clamped().currentPage + 1;
    }

    /** 1-based highest page number for display. */
    public int displayMaxPage() {
        return maxPage() + 1;
    }
}

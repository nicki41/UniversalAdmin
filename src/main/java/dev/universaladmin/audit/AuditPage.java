package dev.universaladmin.audit;

import java.util.List;

/** One page of an {@link AuditQuery} result, plus enough to render pagination controls. */
public record AuditPage(List<AuditEvent> items, int page, int pageSize, long totalCount) {

    public AuditPage {
        items = List.copyOf(items);
    }

    public int totalPages() {
        return totalCount == 0 ? 1 : (int) Math.ceil((double) totalCount / pageSize);
    }

    public boolean hasPrevious() {
        return page > 0;
    }

    public boolean hasNext() {
        return (long) (page + 1) * pageSize < totalCount;
    }
}

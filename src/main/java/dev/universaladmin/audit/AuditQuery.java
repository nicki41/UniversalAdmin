package dev.universaladmin.audit;

import dev.universaladmin.action.Source;
import java.time.Instant;
import java.util.UUID;

/**
 * Filters + pagination for {@link AuditService#query}. Every filter field is
 * nullable and means "no filter on this dimension" - a plain
 * {@code AuditQuery.builder().build()} is "everything, newest first, first
 * page". See docs/architecture/actions.md and docs/user/audit-log.md.
 */
public record AuditQuery(
        UUID actorId,
        String targetId,
        AuditEventType type,
        String module,
        Source source,
        Boolean success,
        Instant from,
        Instant to,
        int page,
        int pageSize) {

    public static final int DEFAULT_PAGE_SIZE = 25;

    public AuditQuery {
        if (page < 0) {
            throw new IllegalArgumentException("page must not be negative, was " + page);
        }
        if (pageSize <= 0) {
            throw new IllegalArgumentException("pageSize must be positive, was " + pageSize);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private UUID actorId;
        private String targetId;
        private AuditEventType type;
        private String module;
        private Source source;
        private Boolean success;
        private Instant from;
        private Instant to;
        private int page = 0;
        private int pageSize = DEFAULT_PAGE_SIZE;

        private Builder() {
        }

        public Builder actorId(UUID actorId) {
            this.actorId = actorId;
            return this;
        }

        public Builder targetId(String targetId) {
            this.targetId = targetId;
            return this;
        }

        public Builder type(AuditEventType type) {
            this.type = type;
            return this;
        }

        public Builder module(String module) {
            this.module = module;
            return this;
        }

        public Builder source(Source source) {
            this.source = source;
            return this;
        }

        public Builder success(Boolean success) {
            this.success = success;
            return this;
        }

        public Builder timeRange(Instant from, Instant to) {
            this.from = from;
            this.to = to;
            return this;
        }

        public Builder page(int page) {
            this.page = page;
            return this;
        }

        public Builder pageSize(int pageSize) {
            this.pageSize = pageSize;
            return this;
        }

        public AuditQuery build() {
            return new AuditQuery(actorId, targetId, type, module, source, success, from, to, page, pageSize);
        }
    }
}

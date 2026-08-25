package dev.universaladmin.action;

import dev.universaladmin.audit.AuditPosition;
import java.util.Map;

/**
 * The extra, action-specific detail an {@link Action} can hand back to
 * {@link ActionExecutor} for the audit entry it builds automatically - the
 * whole point being that a feature developer fills in only what applies
 * (usually just {@link #oldValue()}/{@link #newValue()}, or nothing at all)
 * instead of constructing an entire {@code AuditEvent} by hand. See
 * {@link ActionDefinition.Builder#auditDetails} and docs/architecture/actions.md#audit-hook.
 */
public record AuditDetails(
        String reason, String oldValue, String newValue, String world, AuditPosition position,
        Map<String, Object> metadata, String correlationId) {

    public static final AuditDetails EMPTY = new AuditDetails(null, null, null, null, null, Map.of(), null);

    public AuditDetails {
        metadata = Map.copyOf(metadata);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private String reason;
        private String oldValue;
        private String newValue;
        private String world;
        private AuditPosition position;
        private Map<String, Object> metadata = Map.of();
        private String correlationId;

        private Builder() {
        }

        public Builder reason(String reason) {
            this.reason = reason;
            return this;
        }

        public Builder oldValue(String oldValue) {
            this.oldValue = oldValue;
            return this;
        }

        public Builder newValue(String newValue) {
            this.newValue = newValue;
            return this;
        }

        public Builder world(String world) {
            this.world = world;
            return this;
        }

        public Builder position(AuditPosition position) {
            this.position = position;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public Builder correlationId(String correlationId) {
            this.correlationId = correlationId;
            return this;
        }

        public AuditDetails build() {
            return new AuditDetails(reason, oldValue, newValue, world, position, metadata, correlationId);
        }
    }
}

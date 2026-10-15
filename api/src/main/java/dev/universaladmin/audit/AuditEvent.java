package dev.universaladmin.audit;

import dev.universaladmin.action.ActionTarget;
import dev.universaladmin.action.Actor;
import dev.universaladmin.action.Source;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * One recorded administrative change - the "AUDIT ENTRY" this whole package
 * exists to persist and query. {@code id} is {@code null} until the event
 * has been persisted (assigned by the repository on save); {@code timestamp}
 * is assigned by {@link #builder} at construction time, not by storage.
 *
 * <p>Every field beyond {@code type}/{@code actor}/{@code source}/{@code summary}
 * is optional - most callers never fill most of these directly, because
 * {@link dev.universaladmin.action.ActionExecutor} builds this record
 * automatically from an {@link dev.universaladmin.action.ActionDefinition}
 * and the action's own {@link dev.universaladmin.action.ActionResult} - see
 * docs/architecture/actions.md#audit-hook. Use {@link #builder} directly only
 * for the rare audit entry that does not come from an {@code Action} at all
 * (e.g. a system-level event).
 *
 * @param id            assigned on persistence, {@code null} for a not-yet-saved entry
 * @param timestamp     when the underlying change happened
 * @param actor         who did it
 * @param type          category of event; for an action-sourced entry this mirrors the {@code ActionId} (namespace:name)
 * @param module        owning module key (e.g. {@code "players"}), or {@code null} for core/non-module entries
 * @param target        what was acted on, or {@code null} if the action has no meaningful target
 * @param source        which channel the actor used (GUI/command/web/...)
 * @param success       whether the underlying change actually took effect
 * @param reason        free-text reason (a kick/ban reason, a validation failure message, ...), or {@code null}
 * @param oldValue      the value before the change, or {@code null} if not applicable/unknown
 * @param newValue      the value after the change, or {@code null} if not applicable/unknown
 * @param world         the world the change happened in, or {@code null} if not location-bound
 * @param position      the position the change happened at, or {@code null} if not location-bound
 * @param summary       a short, human-readable one-line description
 * @param metadata      small structured extra data (flat, JSON-serializable values only - see {@code audit.jdbc.MetadataJson})
 * @param correlationId ties multiple entries to one originating request (a web request, a single command invocation), or {@code null}
 */
public record AuditEvent(
        Long id,
        Instant timestamp,
        Actor actor,
        AuditEventType type,
        String module,
        ActionTarget target,
        Source source,
        boolean success,
        String reason,
        String oldValue,
        String newValue,
        String world,
        AuditPosition position,
        String summary,
        Map<String, Object> metadata,
        String correlationId) {

    public AuditEvent {
        Objects.requireNonNull(timestamp, "timestamp");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(summary, "summary");
        metadata = Map.copyOf(metadata);
    }

    public static Builder builder(AuditEventType type, Actor actor, Source source, String summary) {
        return new Builder(type, actor, source, summary);
    }

    public static final class Builder {

        private final AuditEventType type;
        private final Actor actor;
        private final Source source;
        private final String summary;
        private String module;
        private ActionTarget target;
        private boolean success = true;
        private String reason;
        private String oldValue;
        private String newValue;
        private String world;
        private AuditPosition position;
        private Map<String, Object> metadata = Map.of();
        private String correlationId;

        private Builder(AuditEventType type, Actor actor, Source source, String summary) {
            this.type = Objects.requireNonNull(type, "type");
            this.actor = Objects.requireNonNull(actor, "actor");
            this.source = Objects.requireNonNull(source, "source");
            this.summary = Objects.requireNonNull(summary, "summary");
        }

        public Builder module(String module) {
            this.module = module;
            return this;
        }

        public Builder target(ActionTarget target) {
            this.target = target;
            return this;
        }

        public Builder success(boolean success) {
            this.success = success;
            return this;
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

        public AuditEvent build() {
            return new AuditEvent(null, Instant.now(), actor, type, module, target, source, success, reason,
                    oldValue, newValue, world, position, summary, metadata, correlationId);
        }
    }
}

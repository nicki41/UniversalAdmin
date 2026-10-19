package dev.universaladmin.audit;

import dev.universaladmin.core.id.Key;

/** Typed identifier for a kind of audited event, e.g. {@code core:players.kicked}. */
public record AuditEventType(Key key) {

    public static AuditEventType of(String namespace, String name) {
        return new AuditEventType(Key.of(namespace, name));
    }

    public static AuditEventType core(String name) {
        return new AuditEventType(Key.core(name));
    }

    @Override
    public String toString() {
        return key.toString();
    }
}

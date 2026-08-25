package dev.universaladmin.action;

import java.util.Objects;

/**
 * Per-invocation context passed to {@link Action#execute}: who ({@link Actor})
 * and how ({@link Source}). Intentionally small beyond that; if actions later
 * need a locale, a correlation/request id for tracing across the web API,
 * etc., they get added here rather than to the {@link Action} signature.
 */
public record ActionContext(Actor actor, Source source) {

    public ActionContext {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(source, "source");
    }
}

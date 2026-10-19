package dev.universaladmin.action;

import java.util.Objects;

/**
 * A single invocation envelope a frontend hands to {@link ActionExecutor}:
 * which action, who/how (via {@link ActionContext}), and with what input.
 * Exists as its own type (rather than three separate executor arguments) so
 * a frontend can build one value and pass it around - e.g. a GUI
 * confirmation dialog building the request on click and only actually
 * calling the executor once the player confirms.
 */
public record ActionRequest<I>(ActionId id, ActionContext context, I input) {

    public ActionRequest {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(context, "context");
    }

    public static <I> ActionRequest<I> of(ActionId id, Actor actor, Source source, I input) {
        return new ActionRequest<>(id, new ActionContext(actor, source), input);
    }
}

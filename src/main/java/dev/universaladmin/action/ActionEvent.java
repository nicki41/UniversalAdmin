package dev.universaladmin.action;

/**
 * Internal pre/post notifications {@link ActionExecutor} fires around every
 * invocation. Not Bukkit events on purpose - a future web session or
 * extension listener should not need a running Paper event bus to observe
 * these, and {@code action} stays free of Bukkit imports (see docs/architecture/actions.md).
 * Subscribe via {@link ActionExecutor#subscribe(ActionEventListener)}; a
 * future extension API/WebSocket broadcaster is just another listener.
 */
public sealed interface ActionEvent {

    ActionId id();

    ActionContext context();

    /** Fired once, before authorization/validation, for every {@link ActionExecutor#execute} call. */
    record Executing<I>(ActionId id, ActionContext context, I input) implements ActionEvent {
    }

    /** Fired after the wrapped {@link Action} completed successfully (and was audited, if applicable). */
    record Executed<I, R>(ActionId id, ActionContext context, I input, ActionResult.Success<R> result) implements ActionEvent {
    }

    /** Fired for every failure: unknown action id, authorization/validation failure, or a failed/throwing action. */
    record Failed<I, R>(ActionId id, ActionContext context, I input, ActionResult.Failure<R> failure) implements ActionEvent {
    }
}

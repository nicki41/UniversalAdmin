package dev.universaladmin.action;

import java.util.concurrent.CompletableFuture;

/**
 * A single unit of domain logic that mutates or reads server state on behalf
 * of an {@link Actor} - "kick a player", "set the whitelist", "create a
 * backup". Actions are the one place business logic is allowed to live; see
 * docs/architecture/actions.md.
 *
 * <p>Every frontend (GUI click handler, command, and later the web API) calls
 * actions through {@link ActionRegistry} instead of implementing behavior
 * itself. This is what lets the same "kick player" logic be reused by a GUI
 * button, a {@code /admin kick} command, and a future REST endpoint without
 * duplication.
 *
 * @param <I> input type, typically a small record describing what to do
 * @param <R> result type returned on success
 */
public interface Action<I, R> {

    ActionId id();

    /**
     * Runs the action. Implementations that touch the database or other
     * blocking IO must do so off the calling thread (see
     * docs/architecture/threading.md) and complete the returned future from
     * wherever that work finishes.
     */
    CompletableFuture<ActionResult<R>> execute(ActionContext context, I input);
}

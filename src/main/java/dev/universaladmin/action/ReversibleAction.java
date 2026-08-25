package dev.universaladmin.action;

import java.util.concurrent.CompletableFuture;

/**
 * An {@link Action} that can undo its own effect. Not every action needs to
 * implement this - it is opt-in. There is deliberately no undo *history*
 * (stack of past invocations, GUI "undo last action" button, ...) yet; this
 * is just the contract a future undo system builds on, per the ROADMAP.
 *
 * <p>{@code result} is whatever the original {@link #execute} call returned
 * on success - implementations use it to know what to revert to (e.g. a
 * player's gamemode before it was changed).
 */
public interface ReversibleAction<I, R> extends Action<I, R> {

    CompletableFuture<ActionResult<Void>> undo(ActionContext context, I input, R result);
}

package dev.universaladmin.action;

import java.util.Optional;

/**
 * Cheap, synchronous input validation for an {@link Action}, run by
 * {@link ActionExecutor} before the action itself executes - e.g. "is this
 * string a valid gamemode name", not "does this player exist in the
 * database" (that kind of check needs a repository lookup and belongs in
 * the action's own {@link Action#execute}, which can already return an
 * {@link ActionResult.Failure} for it).
 */
@FunctionalInterface
public interface ActionValidator<I> {

    Optional<ValidationError> validate(ActionContext context, I input);

    static <I> ActionValidator<I> none() {
        return (context, input) -> Optional.empty();
    }
}

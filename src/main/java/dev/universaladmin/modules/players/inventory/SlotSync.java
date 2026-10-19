package dev.universaladmin.modules.players.inventory;

import java.util.function.BiPredicate;

/**
 * The three-way merge behind the live inventory editor, for one slot: what
 * the editor shows ({@code view}), what the player really has ({@code
 * target}) and what both agreed on after the previous tick ({@code last}).
 * Whichever side moved away from {@code last} is copied to the other.
 *
 * <p>When both sides changed the same slot within one tick, the player's own
 * inventory wins - it is what the server already acted on (an item the
 * player just used, dropped or ate), so overwriting it from a staff
 * member's view could resurrect an item that no longer exists.
 */
enum SlotSync {

    /** Both sides still agree - nothing to copy. */
    NONE,
    /** The staff member changed the slot in the editor - copy it to the player. */
    TO_TARGET,
    /** The player's inventory changed (or both did) - copy it into the editor. */
    TO_VIEW;

    static <T> SlotSync decide(T view, T target, T last, BiPredicate<T, T> same) {
        boolean viewChanged = !same.test(view, last);
        boolean targetChanged = !same.test(target, last);
        if (targetChanged) {
            return TO_VIEW;
        }
        return viewChanged ? TO_TARGET : NONE;
    }
}

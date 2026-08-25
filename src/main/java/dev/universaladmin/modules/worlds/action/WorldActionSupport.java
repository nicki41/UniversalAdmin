package dev.universaladmin.modules.worlds.action;

import dev.universaladmin.action.ActionResult;
import dev.universaladmin.localization.MessageKey;

/** Shared "world not found" failure every action here can produce - resolving a world by name is common to all of them. */
final class WorldActionSupport {

    private WorldActionSupport() {
    }

    static <R> ActionResult<R> worldNotFound() {
        return ActionResult.failure(ActionResult.FailureReason.NOT_FOUND, MessageKey.of("worlds.action.world-not-found"));
    }
}

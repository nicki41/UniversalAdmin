package dev.universaladmin.modules.moderation.alts;

import dev.universaladmin.action.Action;
import dev.universaladmin.action.ActionContext;
import dev.universaladmin.action.ActionId;
import dev.universaladmin.action.ActionResult;
import dev.universaladmin.localization.MessageKey;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Looking at a player's possible alts and deleting their stored addresses -
 * both actions, so both are permission-checked and audited (who looked at
 * whose connections is exactly what an audit trail is for).
 */
public final class AltActions {

    public static final ActionId VIEW = ActionId.core("moderation.alts.view");
    public static final ActionId FORGET = ActionId.core("moderation.alts.forget");

    private AltActions() {
    }

    /** Whose alts, or whose addresses to forget. */
    public record Target(UUID playerId, String playerName) {
    }

    /** Possible alts of a player. */
    public static final class View implements Action<Target, List<AltMatch>> {

        private final AltService alts;

        public View(AltService alts) {
            this.alts = alts;
        }

        @Override
        public ActionId id() {
            return VIEW;
        }

        @Override
        public CompletableFuture<ActionResult<List<AltMatch>>> execute(ActionContext context, Target input) {
            return alts.alts(input.playerId()).thenApply(ActionResult::success);
        }
    }

    /** Deletes every stored address of a player. Result: how many. */
    public static final class Forget implements Action<Target, Integer> {

        private final AltService alts;

        public Forget(AltService alts) {
            this.alts = alts;
        }

        @Override
        public ActionId id() {
            return FORGET;
        }

        @Override
        public CompletableFuture<ActionResult<Integer>> execute(ActionContext context, Target input) {
            return alts.forget(input.playerId()).thenApply(count -> ActionResult.success(count,
                    MessageKey.of("moderation.alts.forgotten"), String.valueOf(count), input.playerName()));
        }
    }
}

package dev.universaladmin.modules.players.action;

import dev.universaladmin.action.Action;
import dev.universaladmin.action.ActionContext;
import dev.universaladmin.action.ActionId;
import dev.universaladmin.action.ActionResult;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.modules.players.PlayerProfile;
import dev.universaladmin.modules.players.PlayerService;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Reference implementation of the action pattern: a thin wrapper around
 * {@link PlayerService} that any frontend (GUI, command, future web API) can
 * invoke through {@link dev.universaladmin.action.ActionRegistry} by id
 * instead of calling {@link PlayerService} directly. See docs/architecture/actions.md.
 */
public final class GetPlayerProfileAction implements Action<UUID, PlayerProfile> {

    public static final ActionId ID = PlayerActionIds.GET_PROFILE;

    private final PlayerService playerService;

    public GetPlayerProfileAction(PlayerService playerService) {
        this.playerService = playerService;
    }

    @Override
    public ActionId id() {
        return ID;
    }

    @Override
    public CompletableFuture<ActionResult<PlayerProfile>> execute(ActionContext context, UUID input) {
        return playerService.findProfile(input).thenApply(profile -> profile
                .<ActionResult<PlayerProfile>>map(ActionResult::success)
                .orElseGet(() -> ActionResult.failure(
                        ActionResult.FailureReason.NOT_FOUND, MessageKey.of("players.not-found"), input)));
    }
}

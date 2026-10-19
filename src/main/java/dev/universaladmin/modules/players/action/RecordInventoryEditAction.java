package dev.universaladmin.modules.players.action;

import dev.universaladmin.action.Action;
import dev.universaladmin.action.ActionContext;
import dev.universaladmin.action.ActionId;
import dev.universaladmin.action.ActionResult;
import dev.universaladmin.localization.MessageKey;
import java.util.concurrent.CompletableFuture;

/**
 * Records a finished live editing session ({@code LiveInventoryService}).
 * The items themselves already moved slot by slot while the editor was
 * open - that is what makes it live - so this action changes nothing; it is
 * the session's single pass through {@link dev.universaladmin.action.ActionExecutor}:
 * permission check, one audit entry and one {@code ActionEvent} for the
 * whole session instead of one per click.
 */
public final class RecordInventoryEditAction implements Action<InventoryEditRecord, InventoryChangeSummary> {

    private final ActionId id;
    private final MessageKey successMessage;

    /** @param successMessage takes (player name, changed slots) */
    public RecordInventoryEditAction(ActionId id, MessageKey successMessage) {
        this.id = id;
        this.successMessage = successMessage;
    }

    @Override
    public ActionId id() {
        return id;
    }

    @Override
    public CompletableFuture<ActionResult<InventoryChangeSummary>> execute(ActionContext context, InventoryEditRecord input) {
        InventoryChangeSummary summary = input.summary();
        return CompletableFuture.completedFuture(ActionResult.success(summary, successMessage,
                input.targetName(), summary.changedSlots(), summary.nonEmptyAfter(), summary.slots()));
    }
}

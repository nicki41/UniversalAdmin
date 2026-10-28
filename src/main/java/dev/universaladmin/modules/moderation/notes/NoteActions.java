package dev.universaladmin.modules.moderation.notes;

import dev.universaladmin.action.Action;
import dev.universaladmin.action.ActionContext;
import dev.universaladmin.action.ActionId;
import dev.universaladmin.action.ActionResult;
import dev.universaladmin.action.ActionResult.FailureReason;
import dev.universaladmin.core.PlayerNames;
import dev.universaladmin.localization.MessageKey;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Adding and removing staff notes - the only two ways notes change. */
public final class NoteActions {

    public static final ActionId ADD = ActionId.core("moderation.notes.add");
    public static final ActionId REMOVE = ActionId.core("moderation.notes.remove");

    /** Longest note accepted; longer input is rejected rather than silently cut. */
    public static final int MAX_LENGTH = 512;

    private NoteActions() {
    }

    /** @param text the note, as typed */
    public record AddInput(UUID targetId, String targetName, String text) {
    }

    /** Adds a note about a player. */
    public static final class Add implements Action<AddInput, PlayerNote> {

        private final PlayerNoteRepository notes;

        public Add(PlayerNoteRepository notes) {
            this.notes = notes;
        }

        @Override
        public ActionId id() {
            return ADD;
        }

        @Override
        public CompletableFuture<ActionResult<PlayerNote>> execute(ActionContext context, AddInput input) {
            PlayerNote note = new PlayerNote(0, input.targetId(), PlayerNames.real(input.targetId(), input.targetName()), context.actor().playerId(),
                    context.actor().displayName(), input.text().trim(), Instant.now());
            return notes.save(note).thenApply(saved -> ActionResult.success(saved, MessageKey.of("moderation.notes.added"),
                    saved.targetName(), Long.toString(saved.id())));
        }
    }

    /** Deletes a note by id. Result: the deleted note. */
    public static final class Remove implements Action<Long, PlayerNote> {

        private final PlayerNoteRepository notes;

        public Remove(PlayerNoteRepository notes) {
            this.notes = notes;
        }

        @Override
        public ActionId id() {
            return REMOVE;
        }

        @Override
        public CompletableFuture<ActionResult<PlayerNote>> execute(ActionContext context, Long id) {
            return notes.findById(id).thenCompose(found -> found.isEmpty()
                    ? CompletableFuture.completedFuture(ActionResult.<PlayerNote>failure(
                            FailureReason.NOT_FOUND, MessageKey.of("moderation.notes.not-found"), String.valueOf(id)))
                    : notes.deleteById(id).thenApply(ignored -> ActionResult.success(found.get(), MessageKey.of("moderation.notes.removed"),
                            Long.toString(found.get().id()), found.get().targetName())));
        }
    }
}

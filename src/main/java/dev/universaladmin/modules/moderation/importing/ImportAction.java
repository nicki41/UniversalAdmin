package dev.universaladmin.modules.moderation.importing;

import dev.universaladmin.action.Action;
import dev.universaladmin.action.ActionContext;
import dev.universaladmin.action.ActionId;
import dev.universaladmin.action.ActionResult;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.scheduler.TaskScheduler;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.IntConsumer;

/**
 * Imports everything new from one source ({@code /admin import <source>
 * confirm}) - the writing half of {@link ImportService}, run through the
 * action pipeline so it is permission-checked and audited with its counts.
 * The work itself happens off the main thread.
 */
public final class ImportAction implements Action<ImportAction.Input, ImportSummary> {

    public static final ActionId ID = ActionId.core("moderation.import");

    /** @param progress receives the running total after every batch; may be called from any thread */
    public record Input(String source, IntConsumer progress) {
        public Input {
            progress = progress == null ? count -> { } : progress;
        }
    }

    private final ImportService service;
    private final TaskScheduler scheduler;

    public ImportAction(ImportService service, TaskScheduler scheduler) {
        this.service = service;
        this.scheduler = scheduler;
    }

    @Override
    public ActionId id() {
        return ID;
    }

    @Override
    public CompletableFuture<ActionResult<ImportSummary>> execute(ActionContext context, Input input) {
        Optional<PunishmentImporter> importer = service.importer(input.source());
        if (importer.isEmpty()) {
            return CompletableFuture.completedFuture(ActionResult.failure(ActionResult.FailureReason.NOT_FOUND,
                    MessageKey.of("moderation.import.unknown-source"), input.source(), sourceList()));
        }
        return scheduler.<ActionResult<ImportSummary>>supplyAsync(() -> {
            try {
                ImportSummary summary = service.run(importer.get(), input.progress());
                return new ActionResult.Success<ImportSummary>(summary, Map.of(), MessageKey.of("moderation.import.done"), List.of(
                        String.valueOf(summary.totalPunishments()), String.valueOf(summary.notes()), summary.source(),
                        String.valueOf(Math.max(1, Math.round(summary.took().toMillis() / 1000.0))),
                        String.valueOf(summary.players()), String.valueOf(summary.totalSkipped()),
                        String.valueOf(summary.alreadyImported())));
            } catch (ImportException e) {
                ActionResult.FailureReason reason = switch (e.messageKey().value()) {
                    case "moderation.import.error.running" -> ActionResult.FailureReason.CONFLICT;
                    case "moderation.import.error.not-found" -> ActionResult.FailureReason.NOT_FOUND;
                    default -> ActionResult.FailureReason.INTERNAL_ERROR;
                };
                return ActionResult.<ImportSummary>failure(reason, e.getMessage(), e.messageKey(), Map.of(), e.messageArgs());
            }
        });
    }

    /** "litebans, advancedban, vanilla, essentials" - for the unknown-source reply. */
    String sourceList() {
        return String.join(", ", service.importers().stream().map(PunishmentImporter::id).toList());
    }
}

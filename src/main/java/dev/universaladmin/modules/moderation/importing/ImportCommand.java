package dev.universaladmin.modules.moderation.importing;

import dev.universaladmin.command.BaseAdminCommand;
import dev.universaladmin.command.CommandInvocation;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.modules.moderation.ModerationPermissions;
import dev.universaladmin.modules.moderation.PunishmentType;
import dev.universaladmin.scheduler.TaskScheduler;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@code /admin import} - lists the sources found on this server,
 * {@code /admin import <source>} previews (writes nothing),
 * {@code /admin import <source> confirm} imports through {@link ImportAction}.
 */
public final class ImportCommand extends BaseAdminCommand {

    /** How often the running import reports progress, in imported entries. */
    private static final int PROGRESS_EVERY = 2000;

    private static final List<PunishmentType> PREVIEW_ORDER = List.of(
            PunishmentType.BAN, PunishmentType.TEMP_BAN, PunishmentType.IP_BAN, PunishmentType.MUTE,
            PunishmentType.TEMP_MUTE, PunishmentType.WARN, PunishmentType.KICK);

    private final ImportService service;
    private final TaskScheduler scheduler;
    private final Logger logger;

    public ImportCommand(ImportService service, TaskScheduler scheduler, Logger logger) {
        super("import", ModerationPermissions.IMPORT, MessageKey.of("commands.import.usage"), MessageKey.of("commands.import.description"));
        this.service = service;
        this.scheduler = scheduler;
        this.logger = logger;
    }

    @Override
    public void execute(CommandInvocation invocation) {
        if (invocation.argCount() == 0) {
            listSources(invocation);
            return;
        }
        String id = invocation.args().get(0).toLowerCase(Locale.ROOT);
        Optional<PunishmentImporter> importer = service.importer(id);
        if (importer.isEmpty()) {
            invocation.reply("moderation.import.unknown-source", invocation.args().get(0), sourceIds());
            return;
        }
        if (invocation.argCount() >= 2 && invocation.args().get(invocation.argCount() - 1).equalsIgnoreCase("confirm")) {
            runImport(invocation, importer.get());
        } else {
            preview(invocation, importer.get());
        }
    }

    private void listSources(CommandInvocation invocation) {
        scheduler.supplyAsync(() -> service.importers().stream()
                        .map(importer -> new Located(importer, importer.locate()))
                        .toList())
                .whenComplete((sources, error) -> {
                    if (error != null) {
                        logger.log(Level.WARNING, "Could not look for importable plugins", error);
                        invocation.reply("command.error.internal");
                        return;
                    }
                    invocation.reply("moderation.import.sources-header");
                    sources.forEach(source -> source.location().ifPresentOrElse(
                            location -> invocation.line("moderation.import.source-found", source.importer().displayName(),
                                    location, source.importer().id()),
                            () -> invocation.line("moderation.import.source-missing", source.importer().displayName())));
                    invocation.line("moderation.import.sources-hint");
                });
    }

    private void preview(CommandInvocation invocation, PunishmentImporter importer) {
        invocation.reply("moderation.import.checking", importer.displayName());
        scheduler.supplyAsync(() -> {
            try {
                return service.preview(importer);
            } catch (ImportException e) {
                throw new PreviewFailed(e);
            }
        }).whenComplete((summary, error) -> {
            if (error != null) {
                Throwable cause = error.getCause() != null ? error.getCause() : error;
                if (cause instanceof PreviewFailed failed) {
                    invocation.reply(failed.importException.messageKey(), failed.importException.messageArgs());
                } else {
                    logger.log(Level.WARNING, "Import preview of " + importer.id() + " failed", cause);
                    invocation.reply("command.error.internal");
                }
                return;
            }
            printPreview(invocation, importer, summary);
        });
    }

    private void printPreview(CommandInvocation invocation, PunishmentImporter importer, ImportSummary summary) {
        invocation.reply("moderation.import.preview-header", summary.source(), summary.location());
        for (PunishmentType type : PREVIEW_ORDER) {
            int count = summary.count(type);
            if (count == 0) {
                continue;
            }
            String label = invocation.messages().get(MessageKey.of("moderation.import.type." + type.name().toLowerCase(Locale.ROOT)));
            if (type == PunishmentType.KICK) {
                invocation.line("moderation.import.preview-line-plain", label, String.valueOf(count));
            } else {
                invocation.line("moderation.import.preview-line", label, String.valueOf(count), String.valueOf(summary.activeCount(type)));
            }
        }
        if (summary.notes() > 0) {
            invocation.line("moderation.import.preview-notes", String.valueOf(summary.notes()));
        }
        invocation.line("moderation.import.preview-players", String.valueOf(summary.players()));
        if (summary.totalSkipped() > 0) {
            invocation.line("moderation.import.preview-skipped", String.valueOf(summary.totalSkipped()),
                    String.valueOf(summary.skipped().getOrDefault(ImportSink.SkipReason.NO_PLAYER, 0)),
                    String.valueOf(summary.skipped().getOrDefault(ImportSink.SkipReason.UNSUPPORTED, 0)),
                    String.valueOf(summary.skipped().getOrDefault(ImportSink.SkipReason.INVALID, 0)));
        }
        if (summary.alreadyImported() > 0) {
            invocation.line("moderation.import.preview-already", String.valueOf(summary.alreadyImported()));
        }
        if (summary.totalPunishments() + summary.notes() == 0) {
            invocation.line("moderation.import.preview-nothing", summary.source());
            return;
        }
        importer.afterImportNoteKey().ifPresent(key -> invocation.line(key, importer.displayName()));
        invocation.line("moderation.import.preview-confirm", importer.id());
    }

    private void runImport(CommandInvocation invocation, PunishmentImporter importer) {
        invocation.reply("moderation.import.started", importer.displayName());
        AtomicInteger reported = new AtomicInteger();
        invocation.run(ImportAction.ID, new ImportAction.Input(importer.id(), count -> {
            int step = count / PROGRESS_EVERY;
            if (step > reported.get()) {
                reported.set(step);
                invocation.line("moderation.import.progress", String.valueOf(count));
            }
        }));
    }

    private String sourceIds() {
        return String.join(", ", service.importers().stream().map(PunishmentImporter::id).toList());
    }

    @Override
    public List<String> complete(CommandInvocation invocation) {
        if (invocation.argCount() <= 1) {
            return new ArrayList<>(service.importers().stream().map(PunishmentImporter::id).toList());
        }
        return invocation.argCount() == 2 ? List.of("confirm") : List.of();
    }

    private record Located(PunishmentImporter importer, Optional<String> location) {
    }

    /** Carries an {@link ImportException} out of the async preview. */
    private static final class PreviewFailed extends RuntimeException {
        private final transient ImportException importException;

        PreviewFailed(ImportException importException) {
            super(importException);
            this.importException = importException;
        }
    }
}

package dev.universaladmin.modules.reports.command;

import dev.universaladmin.command.AdminCommand;
import dev.universaladmin.command.Arguments;
import dev.universaladmin.command.BaseAdminCommand;
import dev.universaladmin.command.CommandInvocation;
import dev.universaladmin.core.PlayerRef;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.modules.reports.Report;
import dev.universaladmin.modules.reports.ReportService;
import dev.universaladmin.modules.reports.ReportsPermissions;
import dev.universaladmin.modules.reports.action.CreateReportAction;
import dev.universaladmin.modules.reports.action.ReportActionIds;
import dev.universaladmin.modules.reports.action.UpdateReportAction;
import dev.universaladmin.modules.reports.gui.ReportsGui;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** {@code /report <player> <reason>} for everyone, {@code /admin reports ...} for staff. */
public final class ReportCommands {

    private static final int LIST_LIMIT = 10;

    private ReportCommands() {
    }

    public static List<AdminCommand> all(ReportService reports, ReportsGui gui) {
        return List.of(new FileReport(), new Reports(reports, gui));
    }

    /** {@code /report <player> <reason>} - registered as a top-level command even with admin shortcuts off. */
    static final class FileReport extends BaseAdminCommand {

        FileReport() {
            super("report", ReportsPermissions.CREATE, MessageKey.of("commands.report.usage"), MessageKey.of("commands.report.description"));
            shortcuts("report");
            shortcutsAlwaysRegistered();
            playersOnly();
        }

        @Override
        public void execute(CommandInvocation invocation) {
            if (invocation.argCount() < 2) {
                invocation.usage(this);
                return;
            }
            Optional<PlayerRef> target = invocation.knownPlayer(0);
            if (target.isEmpty()) {
                return;
            }
            PlayerRef player = target.get();
            invocation.run(ReportActionIds.CREATE, new CreateReportAction.Input(player.id(), player.name(), invocation.joinArgs(1)),
                    MessageKey.of("reports.filed"), player.name());
        }

        @Override
        public List<String> complete(CommandInvocation invocation) {
            return invocation.argCount() <= 1 ? Arguments.visiblePlayerNames(invocation.sender()) : List.of();
        }
    }

    /** {@code /admin reports [open|all|view <id>|claim <id>|resolve <id> [note]|reject <id> [note]]}. */
    static final class Reports extends BaseAdminCommand {

        private static final List<String> SUBCOMMANDS = List.of("open", "all", "view", "claim", "resolve", "reject");

        private final ReportService reports;
        private final ReportsGui gui;

        Reports(ReportService reports, ReportsGui gui) {
            super("reports", ReportsPermissions.VIEW, MessageKey.of("commands.reports.usage"), MessageKey.of("commands.reports.description"));
            this.reports = reports;
            this.gui = gui;
        }

        @Override
        public void execute(CommandInvocation invocation) {
            String sub = invocation.arg(0).map(arg -> arg.toLowerCase(Locale.ROOT)).orElse("");
            switch (sub) {
                case "" -> {
                    if (invocation.player().isPresent()) {
                        gui.openList(invocation.player().get());
                    } else {
                        list(invocation, false);
                    }
                }
                case "open" -> list(invocation, false);
                case "all" -> list(invocation, true);
                case "view" -> withId(invocation, id -> {
                    if (invocation.player().isPresent()) {
                        gui.openDetail(invocation.player().get(), id);
                    } else {
                        show(invocation, id);
                    }
                });
                case "claim" -> withId(invocation, id -> update(invocation, ReportActionIds.CLAIM, id, null, "reports.claimed"));
                case "resolve" -> withId(invocation, id -> update(invocation, ReportActionIds.RESOLVE, id, invocation.joinArgs(2), "reports.resolved"));
                case "reject" -> withId(invocation, id -> update(invocation, ReportActionIds.REJECT, id, invocation.joinArgs(2), "reports.rejected"));
                default -> invocation.usage(this);
            }
        }

        private void list(CommandInvocation invocation, boolean all) {
            (all ? reports.recent(LIST_LIMIT) : reports.open(LIST_LIMIT)).whenComplete((list, error) -> {
                if (error != null) {
                    invocation.reply("command.error.internal");
                    return;
                }
                if (list.isEmpty()) {
                    invocation.reply(all ? "commands.reports.none" : "commands.reports.none-open");
                    return;
                }
                invocation.line(all ? "commands.reports.header-all" : "commands.reports.header-open", list.size());
                list.forEach(report -> invocation.line("commands.reports.entry", String.valueOf(report.id()), report.targetName(),
                        report.reporterName(), report.reason(), report.status().name().toLowerCase(Locale.ROOT)));
            });
        }

        private void show(CommandInvocation invocation, long id) {
            reports.find(id).whenComplete((found, error) -> {
                if (error != null || found.isEmpty()) {
                    invocation.reply("reports.action.not-found", String.valueOf(id));
                    return;
                }
                Report report = found.get();
                invocation.line("commands.reports.detail", String.valueOf(report.id()), report.targetName(), report.reporterName(),
                        report.reason(), report.status().name().toLowerCase(Locale.ROOT),
                        report.handlerName() == null ? "-" : report.handlerName());
            });
        }

        private void update(CommandInvocation invocation, dev.universaladmin.action.ActionId action, long id, String note, String successKey) {
            invocation.<UpdateReportAction.Input, Report>run(action, new UpdateReportAction.Input(id, note),
                    report -> invocation.reply(successKey, String.valueOf(id), report.targetName()));
        }

        private void withId(CommandInvocation invocation, java.util.function.LongConsumer action) {
            if (invocation.argCount() < 2) {
                invocation.usage(this);
                return;
            }
            String raw = invocation.args().get(1).replace("#", "");
            try {
                action.accept(Long.parseLong(raw));
            } catch (NumberFormatException e) {
                invocation.reply("command.error.invalid-number", raw);
            }
        }

        @Override
        public List<String> complete(CommandInvocation invocation) {
            return invocation.argCount() <= 1 ? SUBCOMMANDS : List.of();
        }
    }
}

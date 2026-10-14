package dev.universaladmin.modules.auditlog.command;

import dev.universaladmin.audit.AuditEvent;
import dev.universaladmin.audit.AuditQuery;
import dev.universaladmin.audit.AuditService;
import dev.universaladmin.command.Arguments;
import dev.universaladmin.command.BaseAdminCommand;
import dev.universaladmin.command.CommandInvocation;
import dev.universaladmin.core.PlayerRef;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.permission.PermissionNode;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * {@code /admin audit [player] [page]} - the newest audit entries, optionally
 * only those a given player performed. The GUI ({@code /admin} → Audit Log)
 * has the full filter set; this is the quick look from chat or the console.
 */
public final class AuditCommand extends BaseAdminCommand {

    private static final int PAGE_SIZE = 10;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final AuditService audit;

    public AuditCommand(AuditService audit, PermissionNode viewPermission) {
        super("audit", viewPermission, MessageKey.of("commands.audit.usage"), MessageKey.of("commands.audit.description"));
        this.audit = audit;
        aliases("log", "auditlog");
    }

    @Override
    public void execute(CommandInvocation invocation) {
        AuditQuery.Builder query = AuditQuery.builder().pageSize(PAGE_SIZE);
        int pageArgIndex = 0;
        String actorName = null;
        if (invocation.argCount() > 0 && Arguments.integer(invocation.args().get(0)).isEmpty()) {
            Optional<PlayerRef> actor = invocation.knownPlayer(0);
            if (actor.isEmpty()) {
                return;
            }
            query.actorId(actor.get().id());
            actorName = actor.get().name();
            pageArgIndex = 1;
        }
        int page = invocation.arg(pageArgIndex).map(Arguments::integer)
                .filter(java.util.OptionalInt::isPresent)
                .map(java.util.OptionalInt::getAsInt)
                .orElse(1);
        query.page(Math.max(0, page - 1));
        String filter = actorName;
        audit.query(query.build()).whenComplete((result, error) -> {
            if (error != null) {
                invocation.reply("command.error.internal");
                return;
            }
            if (result.items().isEmpty()) {
                invocation.reply("commands.audit.empty");
                return;
            }
            invocation.line(filter == null ? "commands.audit.header" : "commands.audit.header-actor",
                    result.page() + 1, Math.max(1, result.totalPages()), result.totalCount(), filter == null ? "" : filter);
            for (AuditEvent event : result.items()) {
                invocation.line(event.success() ? "commands.audit.entry" : "commands.audit.entry-failed",
                        TIME.format(event.timestamp()), event.actor().displayName(), event.type().toString(), event.summary());
            }
            if (result.hasNext()) {
                invocation.line("commands.audit.more", invocation.label(), filter == null ? "" : filter + " ", result.page() + 2);
            }
        });
    }

    @Override
    public List<String> complete(CommandInvocation invocation) {
        return invocation.argCount() <= 1 ? Arguments.visiblePlayerNames(invocation.sender()) : List.of("2", "3");
    }
}

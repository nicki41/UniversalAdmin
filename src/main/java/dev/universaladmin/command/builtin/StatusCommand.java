package dev.universaladmin.command.builtin;

import dev.universaladmin.command.AdminCommand;
import dev.universaladmin.command.CommandInvocation;
import dev.universaladmin.core.PluginStatus;
import dev.universaladmin.core.UniversalAdmin;
import dev.universaladmin.core.time.DurationParser;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.permission.PermissionNode;
import java.util.List;
import java.util.stream.Collectors;

/** {@code /admin status} - version, uptime, module and database status. */
public final class StatusCommand implements AdminCommand {

    private final UniversalAdmin platform;

    public StatusCommand(UniversalAdmin platform) {
        this.platform = platform;
    }

    @Override
    public String name() {
        return "status";
    }

    @Override
    public List<String> aliases() {
        return List.of("version", "about");
    }

    @Override
    public PermissionNode permission() {
        return CorePermissions.STATUS;
    }

    @Override
    public MessageKey usage() {
        return MessageKey.of("command.status.usage");
    }

    @Override
    public MessageKey description() {
        return MessageKey.of("command.status.description");
    }

    @Override
    public void execute(CommandInvocation invocation) {
        report(invocation, platform);
    }

    /** Shared with {@link MenuCommand}, which shows this report to the console instead of a GUI. */
    static void report(CommandInvocation invocation, UniversalAdmin platform) {
        PluginStatus status = platform.status();
        invocation.line("command.status.header", status.version());
        invocation.line("command.status.uptime", DurationParser.format(status.uptime()));
        invocation.line("command.status.modules-active", status.activeModules().size(), join(invocation, status.activeModules()));
        if (!status.failedModules().isEmpty()) {
            invocation.line("command.status.modules-failed", join(invocation, status.failedModules()));
        }
        invocation.line("command.status.database-web", status.database(), status.web());
    }

    private static String join(CommandInvocation invocation, List<?> items) {
        return items.isEmpty()
                ? invocation.messages().get(MessageKey.of("common.none"))
                : items.stream().map(Object::toString).collect(Collectors.joining(", "));
    }
}

package dev.universaladmin.command.builtin;

import dev.universaladmin.command.AdminCommand;
import dev.universaladmin.command.CommandInvocation;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.permission.PermissionNode;
import dev.universaladmin.settings.ConfigReloadResult;
import dev.universaladmin.settings.ReloadConfigAction;
import java.util.stream.Collectors;

/**
 * {@code /admin reload} - UniversalAdmin's own safe reload of
 * {@code config.yml} and {@code lang/*.yml} via {@link ReloadConfigAction},
 * never Bukkit's global {@code /reload}.
 */
public final class ReloadCommand implements AdminCommand {

    @Override
    public String name() {
        return "reload";
    }

    @Override
    public PermissionNode permission() {
        return CorePermissions.RELOAD;
    }

    @Override
    public MessageKey usage() {
        return MessageKey.of("command.reload.usage");
    }

    @Override
    public MessageKey description() {
        return MessageKey.of("command.reload.description");
    }

    @Override
    public void execute(CommandInvocation invocation) {
        invocation.<Void, ConfigReloadResult>run(ReloadConfigAction.ID, null, result -> {
            invocation.reply("command.reload.success", result.applied().size());
            if (!result.pendingRestart().isEmpty()) {
                invocation.reply("command.reload.pending-restart",
                        result.pendingRestart().stream().map(Object::toString).collect(Collectors.joining(", ")));
            }
            if (!result.validationErrors().isEmpty()) {
                invocation.reply("command.reload.errors", String.join("; ", result.validationErrors()));
            }
        });
    }
}

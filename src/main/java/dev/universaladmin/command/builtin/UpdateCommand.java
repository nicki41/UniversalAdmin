package dev.universaladmin.command.builtin;

import dev.universaladmin.command.AdminCommand;
import dev.universaladmin.command.CommandInvocation;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.permission.PermissionNode;
import dev.universaladmin.update.ApplyUpdateAction;
import dev.universaladmin.update.UpdateApplyResult;
import java.util.List;

/**
 * {@code /admin update} - downloads the latest GitHub release of UniversalAdmin
 * and of every extension with an update source into Bukkit's
 * {@code plugins/update/} folder (see {@link ApplyUpdateAction}), applied by
 * the server itself on the next restart. Never hot-swaps.
 */
public final class UpdateCommand implements AdminCommand {

    @Override
    public String name() {
        return "update";
    }

    @Override
    public PermissionNode permission() {
        return CorePermissions.UPDATE_APPLY;
    }

    @Override
    public MessageKey usage() {
        return MessageKey.of("command.update.usage");
    }

    @Override
    public MessageKey description() {
        return MessageKey.of("command.update.description");
    }

    @Override
    public void execute(CommandInvocation invocation) {
        invocation.reply("command.update.checking");
        invocation.<Void, List<UpdateApplyResult>>run(ApplyUpdateAction.ID, null, results -> results.forEach(update -> invocation.reply(
                update.updateApplied() ? "command.update.applied" : "command.update.up-to-date", update.version(), update.name())));
    }
}

package dev.universaladmin.command.builtin;

import dev.universaladmin.command.AdminCommand;
import dev.universaladmin.command.CommandInvocation;
import dev.universaladmin.core.UniversalAdmin;
import dev.universaladmin.gui.MainMenuPage;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.permission.PermissionNode;
import java.util.List;
import org.bukkit.entity.Player;

/**
 * {@code /admin menu} - and what a bare {@code /admin} runs. Opens the main
 * menu for a player who may use it; anyone else gets the next best thing:
 * the console sees the status report, a player with command permissions but
 * no menu permission sees the command list.
 */
public final class MenuCommand implements AdminCommand {

    private final UniversalAdmin platform;
    private final AdminCommand help;

    public MenuCommand(UniversalAdmin platform, AdminCommand help) {
        this.platform = platform;
        this.help = help;
    }

    @Override
    public String name() {
        return "menu";
    }

    @Override
    public List<String> aliases() {
        return List.of("gui");
    }

    @Override
    public PermissionNode permission() {
        return null;
    }

    @Override
    public MessageKey usage() {
        return MessageKey.of("command.menu.usage");
    }

    @Override
    public MessageKey description() {
        return MessageKey.of("command.menu.description");
    }

    @Override
    public void execute(CommandInvocation invocation) {
        if (!(invocation.sender() instanceof Player player)) {
            StatusCommand.report(invocation, platform);
            return;
        }
        if (!invocation.hasPermission(CorePermissions.MENU)) {
            help.execute(invocation);
            return;
        }
        platform.guiPages().get(MainMenuPage.ID).ifPresentOrElse(
                page -> page.open(player),
                () -> StatusCommand.report(invocation, platform));
    }
}

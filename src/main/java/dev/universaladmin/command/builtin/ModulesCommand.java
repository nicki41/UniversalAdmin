package dev.universaladmin.command.builtin;

import dev.universaladmin.command.AdminCommand;
import dev.universaladmin.command.CommandInvocation;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.module.Module;
import dev.universaladmin.module.ModuleRegistry;
import dev.universaladmin.module.ModuleState;
import dev.universaladmin.permission.PermissionNode;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** {@code /admin modules} - every registered module (built-in and extension) with its state. */
public final class ModulesCommand implements AdminCommand {

    private final ModuleRegistry modules;

    public ModulesCommand(ModuleRegistry modules) {
        this.modules = modules;
    }

    @Override
    public String name() {
        return "modules";
    }

    @Override
    public List<String> aliases() {
        return List.of("extensions");
    }

    @Override
    public PermissionNode permission() {
        return CorePermissions.STATUS;
    }

    @Override
    public MessageKey usage() {
        return MessageKey.of("command.modules.usage");
    }

    @Override
    public MessageKey description() {
        return MessageKey.of("command.modules.description");
    }

    @Override
    public void execute(CommandInvocation invocation) {
        List<Module> all = modules.all().stream()
                .sorted(Comparator.comparing(module -> module.descriptor().id().toString()))
                .toList();
        invocation.line("command.modules.header", all.size());
        for (Module module : all) {
            ModuleState state = modules.state(module.descriptor().id());
            invocation.line(MessageKey.of("command.modules.entry." + state.name().toLowerCase(Locale.ROOT)),
                    module.descriptor().displayName(), module.descriptor().id().toString(), module.descriptor().description());
        }
    }
}

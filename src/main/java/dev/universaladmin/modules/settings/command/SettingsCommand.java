package dev.universaladmin.modules.settings.command;

import dev.universaladmin.command.BaseAdminCommand;
import dev.universaladmin.command.CommandInvocation;
import dev.universaladmin.gui.GuiRegistry;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.modules.settings.SettingsPermissions;
import dev.universaladmin.modules.settings.gui.SettingsGuiContext;
import dev.universaladmin.modules.settings.gui.SettingsHomePage;
import dev.universaladmin.settings.SetSettingAction;
import dev.universaladmin.settings.SettingChange;
import dev.universaladmin.settings.SettingDefinition;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * {@code /admin settings} opens the settings GUI; {@code list [section]},
 * {@code get <path>}, {@code set <path> <value>} and {@code reset <path>}
 * do the same from chat or the console. Every change goes through
 * {@link SetSettingAction} - validated, saved, audited.
 */
public final class SettingsCommand extends BaseAdminCommand {

    private static final List<String> SUBCOMMANDS = List.of("list", "get", "set", "reset");

    private final SettingsGuiContext ctx;
    private final GuiRegistry pages;

    public SettingsCommand(SettingsGuiContext ctx, GuiRegistry pages) {
        super("settings", SettingsPermissions.MANAGE, MessageKey.of("commands.settings.usage"),
                MessageKey.of("commands.settings.description"));
        this.ctx = ctx;
        this.pages = pages;
        aliases("config", "setting");
    }

    @Override
    public void execute(CommandInvocation invocation) {
        if (invocation.argCount() == 0) {
            if (invocation.player().isPresent()) {
                pages.get(SettingsHomePage.ID).ifPresent(page -> page.open(invocation.player().get()));
            } else {
                list(invocation, null);
            }
            return;
        }
        String sub = invocation.args().get(0).toLowerCase(Locale.ROOT);
        switch (sub) {
            case "list" -> list(invocation, invocation.arg(1).orElse(null));
            case "get" -> withDefinition(invocation, definition -> invocation.reply("commands.settings.value",
                    definition.key().configPath(), ctx.settings().display(definition), formatDefault(definition),
                    definition.description()));
            case "set" -> {
                if (invocation.argCount() < 3) {
                    invocation.usage(this);
                    return;
                }
                apply(invocation, invocation.args().get(1), invocation.joinArgs(2));
            }
            case "reset" -> {
                if (invocation.argCount() < 2) {
                    invocation.usage(this);
                    return;
                }
                apply(invocation, invocation.args().get(1), null);
            }
            default -> invocation.usage(this);
        }
    }

    private void list(CommandInvocation invocation, String section) {
        Map<String, List<SettingDefinition<?>>> sections = ctx.sections();
        if (section == null) {
            invocation.line("commands.settings.sections", String.join(", ", sections.keySet()));
            return;
        }
        List<SettingDefinition<?>> definitions = sections.get(section.toLowerCase(Locale.ROOT));
        if (definitions == null) {
            invocation.reply("commands.settings.unknown-section", section);
            return;
        }
        invocation.line("commands.settings.section-header", section, definitions.size());
        for (SettingDefinition<?> definition : definitions) {
            invocation.line(definition.requiresRestart() ? "commands.settings.entry-restart" : "commands.settings.entry",
                    definition.key().configPath(), ctx.settings().display(definition));
        }
    }

    private void withDefinition(CommandInvocation invocation, java.util.function.Consumer<SettingDefinition<?>> action) {
        if (invocation.argCount() < 2) {
            invocation.usage(this);
            return;
        }
        Optional<SettingDefinition<?>> definition = ctx.registry().byPath(invocation.args().get(1));
        if (definition.isEmpty()) {
            invocation.reply("settings.action.unknown", invocation.args().get(1));
            return;
        }
        action.accept(definition.get());
    }

    private void apply(CommandInvocation invocation, String path, String value) {
        invocation.<SetSettingAction.Input, SettingChange>run(SetSettingAction.ID, new SetSettingAction.Input(path, value), change -> {
            invocation.reply("settings.changed", path, change.oldValue(), change.newValue());
            if (change.pendingRestart()) {
                invocation.reply("settings.pending-restart");
            }
        });
    }

    private static <T> String formatDefault(SettingDefinition<T> definition) {
        return definition.sensitive() ? "******" : definition.type().format(definition.defaultValue());
    }

    @Override
    public List<String> complete(CommandInvocation invocation) {
        int count = invocation.argCount();
        if (count <= 1) {
            return SUBCOMMANDS;
        }
        String sub = invocation.args().get(0).toLowerCase(Locale.ROOT);
        if (count == 2 && sub.equals("list")) {
            return List.copyOf(ctx.sections().keySet());
        }
        if (count == 2 && (sub.equals("get") || sub.equals("set") || sub.equals("reset"))) {
            return ctx.registry().all().stream()
                    .filter(definition -> !definition.sensitive() || sub.equals("get"))
                    .map(definition -> definition.key().configPath())
                    .sorted()
                    .toList();
        }
        if (count == 3 && sub.equals("set")) {
            return ctx.registry().byPath(invocation.args().get(1))
                    .map(definition -> definition.type().suggestions())
                    .orElse(List.of());
        }
        return List.of();
    }
}

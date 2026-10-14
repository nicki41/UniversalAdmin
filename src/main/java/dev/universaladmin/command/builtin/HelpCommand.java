package dev.universaladmin.command.builtin;

import dev.universaladmin.command.AdminCommand;
import dev.universaladmin.command.Arguments;
import dev.universaladmin.command.CommandInvocation;
import dev.universaladmin.command.CommandMessages;
import dev.universaladmin.command.CommandRegistry;
import dev.universaladmin.command.UniversalAdminCommand;
import dev.universaladmin.localization.ComponentMessages;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.localization.MessageService;
import dev.universaladmin.permission.PermissionNode;
import java.util.List;
import java.util.stream.IntStream;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * {@code /admin help [page]} - every command the sender may use, with its
 * synopsis and description, clickable to pre-fill the chat line. Built from
 * {@link CommandRegistry}, so extension commands show up automatically.
 */
public final class HelpCommand implements AdminCommand {

    private static final int PAGE_SIZE = 8;

    private final CommandRegistry registry;

    public HelpCommand(CommandRegistry registry) {
        this.registry = registry;
    }

    @Override
    public String name() {
        return "help";
    }

    @Override
    public List<String> aliases() {
        return List.of("?", "commands");
    }

    @Override
    public PermissionNode permission() {
        return null;
    }

    @Override
    public MessageKey usage() {
        return MessageKey.of("command.help.usage");
    }

    @Override
    public MessageKey description() {
        return MessageKey.of("command.help.description");
    }

    @Override
    public void execute(CommandInvocation invocation) {
        List<AdminCommand> visible = visibleCommands(invocation);
        if (visible.isEmpty()) {
            invocation.reply(CommandMessages.NO_PERMISSION);
            return;
        }
        int pages = pageCount(visible.size());
        int requested = invocation.arg(0)
                .map(Arguments::integer)
                .filter(java.util.OptionalInt::isPresent)
                .map(java.util.OptionalInt::getAsInt)
                .orElse(1);
        int page = Math.max(1, Math.min(requested, pages));

        MessageService messages = invocation.messages();
        String root = rootLabel(invocation.label());
        invocation.line("command.help.header", page, pages);
        visible.stream()
                .skip((long) (page - 1) * PAGE_SIZE)
                .limit(PAGE_SIZE)
                .forEach(command -> invocation.send(entry(messages, root, command)));
        if (page < pages) {
            invocation.send(ComponentMessages.render(messages.get(MessageKey.of("command.help.next"), root, page + 1))
                    .clickEvent(ClickEvent.runCommand("/" + root + " help " + (page + 1))));
        }
    }

    @Override
    public List<String> complete(CommandInvocation invocation) {
        if (invocation.argCount() > 1) {
            return List.of();
        }
        int pages = pageCount(visibleCommands(invocation).size());
        return IntStream.rangeClosed(1, pages).mapToObj(Integer::toString).toList();
    }

    private List<AdminCommand> visibleCommands(CommandInvocation invocation) {
        return registry.all().stream()
                .filter(command -> command != this)
                .filter(command -> UniversalAdminCommand.visibleTo(invocation.sender(), command))
                .toList();
    }

    private static int pageCount(int entries) {
        return Math.max(1, (entries + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    private Component entry(MessageService messages, String root, AdminCommand command) {
        String synopsis = messages.get(command.usage());
        String commandLine = "/" + root + " " + command.name() + (synopsis.isBlank() ? "" : " " + synopsis);
        Component description = ComponentMessages.render(messages.get(command.description()));
        return Component.text(commandLine, NamedTextColor.GOLD)
                .append(Component.text(" - ", NamedTextColor.DARK_GRAY))
                .append(description.colorIfAbsent(NamedTextColor.GRAY))
                .hoverEvent(HoverEvent.showText(ComponentMessages.render(messages.get(MessageKey.of("command.help.hover")))))
                .clickEvent(ClickEvent.suggestCommand("/" + root + " " + command.name() + " "));
    }

    /** {@code ua help} -> {@code ua}: the list uses whatever root label help was invoked through. */
    private static String rootLabel(String label) {
        int space = label.indexOf(' ');
        return space < 0 ? "admin" : label.substring(0, space);
    }
}

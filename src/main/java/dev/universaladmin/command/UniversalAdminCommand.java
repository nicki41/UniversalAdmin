package dev.universaladmin.command;

import dev.universaladmin.action.Actor;
import dev.universaladmin.command.CommandInvocation.CommandEnvironment;
import dev.universaladmin.core.PlayerNames;
import dev.universaladmin.localization.ComponentMessages;
import dev.universaladmin.permission.bukkit.PermissiblePermissionEvaluator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

/**
 * The root {@code /admin} command (aliases {@code /ua}, {@code /uadmin} -
 * see plugin.yml) and the single dispatcher behind every shortcut such as
 * {@code /kick}. It knows no command by name: everything below
 * {@code /admin} is an {@link AdminCommand} in {@link CommandRegistry},
 * contributed by the core, a built-in module, or an extension alike.
 *
 * <p>{@code /admin} on its own runs the {@link #DEFAULT_COMMAND} (the main
 * menu for players, the status report for the console, see
 * {@code MenuCommand}). Unknown subcommands point at {@code /admin help}.
 *
 * <p>Only dispatching lives here: permission/player-only gating for
 * visibility, argument splitting, completion filtering, and a last-resort
 * catch so a buggy command can never surface a stack trace to the sender.
 */
public final class UniversalAdminCommand implements TabExecutor {

    /** Run by a bare {@code /admin}. */
    public static final String DEFAULT_COMMAND = "menu";

    private final CommandRegistry registry;
    private final CommandEnvironment environment;

    public UniversalAdminCommand(CommandRegistry registry, CommandEnvironment environment) {
        this.registry = registry;
        this.environment = environment;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            dispatch(sender, label, DEFAULT_COMMAND, List.of());
        } else {
            dispatch(sender, label, args[0], Arrays.asList(args).subList(1, args.length));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length <= 1) {
            String prefix = args.length == 0 ? "" : args[0];
            List<String> names = new ArrayList<>();
            for (AdminCommand candidate : registry.all()) {
                if (visibleTo(sender, candidate)) {
                    names.add(candidate.name());
                    names.addAll(candidate.aliases());
                }
            }
            return Arguments.matching(names, prefix);
        }
        return complete(sender, label, args[0], Arrays.asList(args).subList(1, args.length));
    }

    /**
     * Runs {@code name} (a subcommand name or alias) for {@code sender} -
     * shared by {@code /admin <name> ...} and the {@code /<shortcut> ...}
     * registrations, which pass their own label.
     */
    public void dispatch(CommandSender sender, String rootLabel, String name, List<String> args) {
        Optional<AdminCommand> found = registry.find(name);
        if (found.isEmpty()) {
            send(sender, CommandMessages.UNKNOWN, rootLabel);
            return;
        }
        AdminCommand target = found.get();
        String label = isShortcut(rootLabel, target) ? rootLabel : rootLabel + " " + target.name();
        dispatchResolved(sender, label, target, args);
    }

    /** Runs an already-resolved command - the shortcut registrations call this directly. */
    public void dispatchResolved(CommandSender sender, String label, AdminCommand target, List<String> args) {
        dispatchResolved(sender, actorFor(sender), label, target, args);
    }

    /**
     * Like {@link #dispatch}, for an actor that is not the sender itself - a
     * Discord user through {@link RemoteCommandService}, whose stand-in sender
     * only carries the replies.
     */
    public void dispatchAs(CommandSender sender, Actor actor, String rootLabel, String name, List<String> args) {
        Optional<AdminCommand> found = registry.find(name);
        if (found.isEmpty()) {
            send(sender, CommandMessages.UNKNOWN, rootLabel);
            return;
        }
        dispatchResolved(sender, actor, rootLabel + " " + found.get().name(), found.get(), args);
    }

    /** Completion for {@link #dispatchAs}. */
    public List<String> completeAs(CommandSender sender, Actor actor, String label, AdminCommand target, List<String> args) {
        CommandInvocation invocation = new CommandInvocation(sender, actor, label, args, environment);
        try {
            return Arguments.matching(target.complete(invocation), invocation.currentArg());
        } catch (RuntimeException e) {
            environment.logger().log(Level.WARNING, "Tab completion for /" + label + " threw", e);
            return List.of();
        }
    }

    private void dispatchResolved(CommandSender sender, Actor actor, String label, AdminCommand target, List<String> args) {
        CommandInvocation invocation = new CommandInvocation(sender, actor, label, args, environment);
        if (!invocation.hasPermission(target.permission())) {
            invocation.reply(CommandMessages.NO_PERMISSION);
            return;
        }
        if (target.playerOnly() && !(sender instanceof Player)) {
            invocation.reply(CommandMessages.PLAYER_ONLY);
            return;
        }
        try {
            target.execute(invocation);
        } catch (RuntimeException e) {
            environment.logger().log(Level.SEVERE, "Command /" + label + " threw", e);
            invocation.reply(CommandMessages.INTERNAL_ERROR);
        }
    }

    /** Completion for the arguments after {@code name}. */
    public List<String> complete(CommandSender sender, String rootLabel, String name, List<String> args) {
        return registry.find(name)
                .filter(target -> visibleTo(sender, target))
                .map(target -> completeResolved(sender, rootLabel + " " + target.name(), target, args))
                .orElse(List.of());
    }

    public List<String> completeResolved(CommandSender sender, String label, AdminCommand target, List<String> args) {
        CommandInvocation invocation = new CommandInvocation(sender, actorFor(sender), label, args, environment);
        try {
            return Arguments.matching(target.complete(invocation), invocation.currentArg());
        } catch (RuntimeException e) {
            environment.logger().log(Level.WARNING, "Tab completion for /" + label + " threw", e);
            return List.of();
        }
    }

    public CommandRegistry registry() {
        return registry;
    }

    /** Whether {@code sender} may see {@code command} in help and completion. */
    public static boolean visibleTo(CommandSender sender, AdminCommand command) {
        return command.permission() == null || sender.hasPermission(command.permission().value());
    }

    public static Actor actorFor(CommandSender sender) {
        if (sender instanceof Player player) {
            return Actor.player(player.getUniqueId(), PlayerNames.real(player), new PermissiblePermissionEvaluator(player));
        }
        return Actor.console();
    }

    private static boolean isShortcut(String rootLabel, AdminCommand target) {
        return target.shortcuts().stream().anyMatch(shortcut -> shortcut.equalsIgnoreCase(rootLabel));
    }

    private void send(CommandSender sender, dev.universaladmin.localization.MessageKey key, Object... args) {
        sender.sendMessage(ComponentMessages.render(
                environment.messages().get(CommandMessages.PREFIX) + environment.messages().get(key, args)));
    }
}

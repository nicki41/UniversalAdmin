package dev.universaladmin.command;

import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.permission.PermissionNode;
import java.util.List;

/**
 * One subcommand of {@code /admin} (aliases {@code /ua}, {@code /uadmin}),
 * e.g. {@code /admin kick <player> <reason>}. Contributed by a module or an
 * extension through {@link CommandRegistry} - built-in modules use exactly
 * this interface too, there is no private command path.
 *
 * <p>A command is a <b>frontend</b>: it parses arguments and calls an
 * {@link dev.universaladmin.action.Action} through
 * {@link CommandInvocation#run} (or a service), never implements business
 * logic itself - see docs/development/architecture-rules.md.
 *
 * <p>{@link #permission()} only decides who <i>sees</i> the command (help,
 * tab completion) and may start it; the action it runs authorizes again on
 * its own through {@link dev.universaladmin.action.ActionExecutor}.
 */
public interface AdminCommand {

    /** The primary name, lower case, no spaces - {@code kick} for {@code /admin kick}. */
    String name();

    /** Alternative names under {@code /admin}, e.g. {@code gm} for {@code gamemode}. */
    default List<String> aliases() {
        return List.of();
    }

    /**
     * Top-level labels that run this command directly, e.g. {@code kick} so
     * {@code /kick} works like {@code /admin kick}. Registered only while
     * {@code commands.shortcuts.enabled} is {@code true} (and the label is
     * not in {@code commands.shortcuts.disabled}), unless
     * {@link #alwaysRegisterShortcuts()} says otherwise. The namespaced form
     * ({@code /universaladmin:kick}) always exists once registered, so a
     * clash with another plugin's command of the same name never makes this
     * one unreachable.
     */
    default List<String> shortcuts() {
        return List.of();
    }

    /**
     * {@code true} for commands regular players are meant to type directly
     * ({@code /report}, {@code /staffchat}) - their {@link #shortcuts()} are
     * registered even when admin shortcuts are switched off.
     */
    default boolean alwaysRegisterShortcuts() {
        return false;
    }

    /** Required to see and run this command, or {@code null} if anyone may. */
    PermissionNode permission();

    /** Plain-text argument synopsis shown after the label, e.g. {@code <player> <reason>}. Not MiniMessage. */
    MessageKey usage();

    /** One-line description for {@code /admin help}. */
    MessageKey description();

    /** Whether only a player (not the console) can run this - e.g. anything that opens a GUI. */
    default boolean playerOnly() {
        return false;
    }

    /** Runs the command. Called on the main thread; must not block. */
    void execute(CommandInvocation invocation);

    /**
     * Tab-completion candidates for the <i>last</i> argument in
     * {@code invocation.args()} (which may be empty while typing). Called on
     * the main thread; must not block. Returned values are filtered by the
     * typed prefix afterwards, so returning every candidate is fine.
     */
    default List<String> complete(CommandInvocation invocation) {
        return List.of();
    }
}

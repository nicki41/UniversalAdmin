package dev.universaladmin.command;

import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.permission.PermissionNode;
import java.util.List;

/**
 * Convenience base for an {@link AdminCommand}: name, permission, usage and
 * description are fixed at construction; aliases, shortcuts and
 * {@link #playerOnly()} are set from the subclass constructor. Purely a
 * boilerplate reducer - implementing {@link AdminCommand} directly is just as
 * valid.
 *
 * <pre>{@code
 * public KickCommand(...) {
 *     super("kick", ModerationPermissions.KICK,
 *             MessageKey.of("commands.kick.usage"), MessageKey.of("commands.kick.description"));
 *     shortcuts("kick");
 * }
 * }</pre>
 */
public abstract class BaseAdminCommand implements AdminCommand {

    private final String name;
    private final PermissionNode permission;
    private final MessageKey usage;
    private final MessageKey description;
    private List<String> aliases = List.of();
    private List<String> shortcuts = List.of();
    private boolean playerOnly;
    private boolean alwaysRegisterShortcuts;

    protected BaseAdminCommand(String name, PermissionNode permission, MessageKey usage, MessageKey description) {
        this.name = name;
        this.permission = permission;
        this.usage = usage;
        this.description = description;
    }

    protected final void aliases(String... aliases) {
        this.aliases = List.of(aliases);
    }

    protected final void shortcuts(String... shortcuts) {
        this.shortcuts = List.of(shortcuts);
    }

    /** Marks the command as runnable by players only (e.g. it opens a GUI). */
    protected final void playersOnly() {
        this.playerOnly = true;
    }

    /** See {@link AdminCommand#alwaysRegisterShortcuts()}. */
    protected final void shortcutsAlwaysRegistered() {
        this.alwaysRegisterShortcuts = true;
    }

    @Override
    public final String name() {
        return name;
    }

    @Override
    public final List<String> aliases() {
        return aliases;
    }

    @Override
    public final List<String> shortcuts() {
        return shortcuts;
    }

    @Override
    public final boolean alwaysRegisterShortcuts() {
        return alwaysRegisterShortcuts;
    }

    @Override
    public final PermissionNode permission() {
        return permission;
    }

    @Override
    public final MessageKey usage() {
        return usage;
    }

    @Override
    public final MessageKey description() {
        return description;
    }

    @Override
    public final boolean playerOnly() {
        return playerOnly;
    }
}

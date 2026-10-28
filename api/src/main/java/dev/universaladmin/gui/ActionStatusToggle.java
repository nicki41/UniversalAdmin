package dev.universaladmin.gui;

import dev.universaladmin.action.ActionExecutor;
import dev.universaladmin.action.ActionId;
import dev.universaladmin.localization.MessageService;
import dev.universaladmin.permission.PermissionNode;
import dev.universaladmin.scheduler.TaskScheduler;
import java.util.function.Function;
import java.util.function.Predicate;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/**
 * A {@link StatusToggle} that runs an action for its viewer - {@code onAction}
 * while it is off, {@code offAction} while it is on (the same id for a
 * plain toggle such as vanish). The action's own result message is shown,
 * like any GUI action.
 *
 * @since 1.7
 */
public final class ActionStatusToggle implements StatusToggle {

    private final String id;
    private final int order;
    private final PermissionNode permission;
    private final Predicate<Player> active;
    private final Function<Player, Component> label;
    private final ActionId onAction;
    private final ActionId offAction;
    private final ActionExecutor actions;
    private final TaskScheduler scheduler;
    private final MessageService messages;

    public ActionStatusToggle(String id, int order, PermissionNode permission, Predicate<Player> active,
            Function<Player, Component> label, ActionId onAction, ActionId offAction, ActionExecutor actions,
            TaskScheduler scheduler, MessageService messages) {
        this.id = id;
        this.order = order;
        this.permission = permission;
        this.active = active;
        this.label = label;
        this.onAction = onAction;
        this.offAction = offAction;
        this.actions = actions;
        this.scheduler = scheduler;
        this.messages = messages;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public int order() {
        return order;
    }

    @Override
    public PermissionNode permission() {
        return permission;
    }

    @Override
    public boolean active(Player viewer) {
        return active.test(viewer);
    }

    @Override
    public Component label(Player viewer) {
        return label.apply(viewer);
    }

    @Override
    public void toggle(GuiClickContext context, Runnable done) {
        ActionId action = active(context.viewer()) ? offAction : onAction;
        GuiActions.runAndReport(actions, scheduler, messages, context.viewer(), action, null, null, done);
    }
}

package dev.universaladmin.modules.nick.gui;

import dev.universaladmin.action.ActionExecutor;
import dev.universaladmin.core.PlayerRef;
import dev.universaladmin.gui.ConfirmationDialog;
import dev.universaladmin.gui.GuiActions;
import dev.universaladmin.gui.GuiClickContext;
import dev.universaladmin.gui.GuiFramework;
import dev.universaladmin.gui.GuiItem;
import dev.universaladmin.gui.PlayerMenuEntry;
import dev.universaladmin.localization.ComponentMessages;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.localization.MessageService;
import dev.universaladmin.modules.nick.NickPermissions;
import dev.universaladmin.modules.nick.action.NickActions;
import dev.universaladmin.permission.PermissionNode;
import dev.universaladmin.scheduler.TaskScheduler;
import dev.universaladmin.status.Nicks;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * "Nick" on a nicked player's profile, for staff who may see real names:
 * the nick and its skin; a click ends it (with {@code universaladmin.nick.others}).
 */
public final class NickProfileEntry implements PlayerMenuEntry {

    private final Nicks nicks;
    private final GuiFramework framework;
    private final MessageService messages;
    private final ActionExecutor actions;
    private final TaskScheduler scheduler;

    public NickProfileEntry(Nicks nicks, GuiFramework framework, MessageService messages, ActionExecutor actions, TaskScheduler scheduler) {
        this.nicks = nicks;
        this.framework = framework;
        this.messages = messages;
        this.actions = actions;
        this.scheduler = scheduler;
    }

    @Override
    public String id() {
        return "core:nick.profile";
    }

    @Override
    public int order() {
        return 70;
    }

    @Override
    public PermissionNode permission() {
        return NickPermissions.SEE;
    }

    @Override
    public boolean visible(Player viewer, PlayerRef target) {
        return nicks.nick(target.id()).isPresent();
    }

    @Override
    public GuiItem icon(Player viewer, PlayerRef target) {
        List<Component> lore = new ArrayList<>();
        nicks.nick(target.id()).ifPresent(nick -> {
            lore.add(text("nick.gui.real-name", nick.realName()));
            lore.add(nick.skinOf() == null ? text("nick.gui.skin-own") : text("nick.gui.skin-of", nick.skinOf()));
            if (viewer.hasPermission(NickPermissions.OTHERS.value())) {
                lore.add(text("nick.gui.remove-other-hint"));
            }
        });
        String name = nicks.nick(target.id()).map(Nicks.Nick::name).orElse("?");
        return GuiItem.of(Material.NAME_TAG, text("nick.gui.profile", name), lore);
    }

    @Override
    public void onClick(GuiClickContext context, PlayerRef target) {
        Player viewer = context.viewer();
        if (!viewer.hasPermission(NickPermissions.OTHERS.value())) {
            return;
        }
        nicks.nick(target.id()).ifPresent(nick -> ConfirmationDialog.open(viewer, framework, messages,
                text("nick.gui.remove-other", nick.name()),
                List.of(text("nick.gui.remove-other-confirm", nick.name(), nick.realName())), ConfirmationDialog.DangerLevel.WARNING,
                confirm -> GuiActions.runAndReport(actions, scheduler, messages, confirm.viewer(), NickActions.CLEAR,
                        new PlayerRef(nick.playerId(), nick.realName()), null, () -> { }),
                GuiClickContext::back));
    }

    private Component text(String key, Object... args) {
        return ComponentMessages.render(messages.get(MessageKey.of(key), args));
    }
}

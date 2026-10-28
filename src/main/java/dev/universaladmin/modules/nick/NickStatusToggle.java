package dev.universaladmin.modules.nick;

import dev.universaladmin.core.PlayerRef;
import dev.universaladmin.core.UniversalAdmin;
import dev.universaladmin.gui.GuiActions;
import dev.universaladmin.gui.GuiClickContext;
import dev.universaladmin.gui.StatusToggle;
import dev.universaladmin.localization.ComponentMessages;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.modules.nick.action.NickActions;
import dev.universaladmin.modules.nick.gui.NickHomePage;
import dev.universaladmin.permission.PermissionNode;
import dev.universaladmin.status.Nicks;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/** "Nick" in the main menu's "My status" row: on shows the nick and ends it; off opens the Nick page to choose one. */
final class NickStatusToggle implements StatusToggle {

    private final Nicks nicks;
    private final UniversalAdmin platform;

    NickStatusToggle(Nicks nicks, UniversalAdmin platform) {
        this.nicks = nicks;
        this.platform = platform;
    }

    @Override
    public String id() {
        return "core:nick.nick";
    }

    @Override
    public int order() {
        return 40;
    }

    @Override
    public PermissionNode permission() {
        return NickPermissions.USE;
    }

    @Override
    public boolean active(Player viewer) {
        return nicks.nick(viewer.getUniqueId()).isPresent();
    }

    @Override
    public Component label(Player viewer) {
        Optional<Nicks.Nick> nick = nicks.nick(viewer.getUniqueId());
        return ComponentMessages.render(nick.isPresent()
                ? platform.messages().get(MessageKey.of("nick.toggle.nicked"), nick.get().name())
                : platform.messages().get(MessageKey.of("nick.toggle.plain")));
    }

    @Override
    public void toggle(GuiClickContext context, Runnable done) {
        Player viewer = context.viewer();
        Optional<Nicks.Nick> nick = nicks.nick(viewer.getUniqueId());
        if (nick.isPresent()) {
            GuiActions.runAndReport(platform.actionExecutor(), platform.scheduler(), platform.messages(), viewer, NickActions.CLEAR,
                    new PlayerRef(viewer.getUniqueId(), nick.get().realName()), null, done);
            return;
        }
        platform.guiPages().get(NickHomePage.ID).ifPresent(context::open);
    }
}

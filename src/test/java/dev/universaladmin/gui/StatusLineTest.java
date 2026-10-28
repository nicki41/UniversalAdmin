package dev.universaladmin.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.universaladmin.permission.PermissionNode;
import dev.universaladmin.settings.CoreSettings;
import dev.universaladmin.settings.SettingsService;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** The status line names what is on, steps aside while paused, and stays away while nothing is on. */
class StatusLineTest {

    private final Server server = mock(Server.class);
    private final SettingsService settings = mock(SettingsService.class);
    private final StatusToggleRegistry toggles = new StatusToggleRegistry();
    private final Player player = mock(Player.class);
    private final StatusLine line = new StatusLine(server, toggles, settings, Logger.getAnonymousLogger());

    StatusLineTest() {
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.hasPermission(any(String.class))).thenReturn(true);
        doReturn(List.of(player)).when(server).getOnlinePlayers();
        when(settings.get(CoreSettings.GUI_STATUS_LINE)).thenReturn(true);
    }

    private static StatusToggle toggle(String id, int order, String label, boolean on) {
        return new StatusToggle() {
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
                return PermissionNode.core("test");
            }

            @Override
            public boolean active(Player viewer) {
                return on;
            }

            @Override
            public Component label(Player viewer) {
                return Component.text(label);
            }

            @Override
            public void toggle(GuiClickContext context, Runnable done) {
            }
        };
    }

    @Test
    void namesWhatIsOnInOrder() {
        toggles.register(toggle("test:godmode", 30, "Godmode", true));
        toggles.register(toggle("test:vanish", 10, "Vanish", true));
        toggles.register(toggle("test:staffmode", 20, "Staff mode", false));

        line.run();

        ArgumentCaptor<Component> bar = ArgumentCaptor.forClass(Component.class);
        verify(player).sendActionBar(bar.capture());
        assertEquals("Vanish · Godmode", PlainTextComponentSerializer.plainText().serialize(bar.getValue()));
    }

    @Test
    void nothingWhileNothingIsOn() {
        toggles.register(toggle("test:vanish", 10, "Vanish", false));

        line.run();

        verify(player, never()).sendActionBar(any(Component.class));
    }

    @Test
    void stepsAsideWhilePaused() {
        toggles.register(toggle("test:vanish", 10, "Vanish", true));
        toggles.pauseStatusLine(player.getUniqueId(), Duration.ofSeconds(5));

        line.run();

        verify(player, never()).sendActionBar(any(Component.class));
    }

    @Test
    void offWhenTheSettingIsOff() {
        toggles.register(toggle("test:vanish", 10, "Vanish", true));
        when(settings.get(CoreSettings.GUI_STATUS_LINE)).thenReturn(false);

        line.run();

        verify(player, never()).sendActionBar(any(Component.class));
    }
}

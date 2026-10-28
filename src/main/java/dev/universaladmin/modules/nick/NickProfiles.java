package dev.universaladmin.modules.nick;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.TranslationArgument;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Puts a nick on a player and takes it off again, through Paper's
 * {@code Player#setPlayerProfile}: the profile's name and skin are what
 * clients show in the tab list, above the head and for the skin, and what
 * {@code Player#getName()} returns to every plugin. Main thread only.
 *
 * <p>Set while the player joins (before Paper announces them to the
 * others), the nick is simply the name they arrive with. Set later, Paper
 * shows the player anew to everyone who sees them and respawns them in
 * place for themselves, so their own client picks up the skin.
 */
final class NickProfiles {

    private static final String TEXTURES = "textures";

    private NickProfiles() {
    }

    /** {@code real} with the nick's name, and its skin unless the player keeps their own. */
    static PlayerProfile disguise(PlayerProfile real, NickRecord nick) {
        PlayerProfile profile = Bukkit.createProfileExact(real.getId(), nick.name());
        for (ProfileProperty property : real.getProperties()) {
            if (nick.skin() == null || !property.getName().equals(TEXTURES)) {
                profile.setProperty(property);
            }
        }
        if (nick.skin() != null) {
            profile.setProperty(new ProfileProperty(TEXTURES, nick.skin().value(), nick.skin().signature()));
        }
        return profile;
    }

    static void wear(Player player, PlayerProfile profile) {
        player.setPlayerProfile(profile);
        // Chat and other plugins use the display name - it was set from the real name at login.
        player.displayName(Component.text(profile.getName()));
    }

    static void giveBack(Player player, NickRuntimeState.Active active) {
        player.setPlayerProfile(active.realProfile());
        player.displayName(active.realDisplayName());
    }

    /**
     * The vanilla join message names the player by the name they logged in
     * with - for a nicked player it is rebuilt with the nick (and without
     * the "formerly known as" variant). A message another plugin set is
     * left alone.
     */
    static Component joinMessage(Component message, String nick) {
        if (message instanceof TranslatableComponent translatable && translatable.key().startsWith("multiplayer.player.joined")) {
            return Component.translatable("multiplayer.player.joined", NamedTextColor.YELLOW, Component.text(nick));
        }
        return message;
    }

    /**
     * Removes the "show entity" hover from the names of nicked players in a
     * message (death messages carry one): it shows the player's UUID, which
     * leads straight to their real name.
     */
    static Component withoutEntityHovers(Component message, Set<UUID> nicked) {
        if (message == null || nicked.isEmpty()) {
            return message;
        }
        Component result = message;
        HoverEvent<?> hover = result.hoverEvent();
        if (hover != null && hover.value() instanceof HoverEvent.ShowEntity entity && nicked.contains(entity.id())) {
            result = result.hoverEvent(null);
        }
        if (result instanceof TranslatableComponent translatable && !translatable.arguments().isEmpty()) {
            List<TranslationArgument> arguments = translatable.arguments().stream()
                    .map(argument -> argument.value() instanceof Component component
                            ? TranslationArgument.component(withoutEntityHovers(component, nicked))
                            : argument)
                    .toList();
            result = translatable.arguments(arguments);
        }
        if (!result.children().isEmpty()) {
            result = result.children(result.children().stream().map(child -> withoutEntityHovers(child, nicked)).toList());
        }
        return result;
    }
}

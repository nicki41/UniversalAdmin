package dev.universaladmin.localization;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

/**
 * Turns a resolved {@link MessageService} string into an Adventure
 * {@link Component} for sending to a Bukkit {@code CommandSender}/{@code Player}.
 *
 * <p>Deliberately separate from {@link MessageService} itself: the service
 * returns plain {@code String}s (parameter-substituted, but still containing
 * any MiniMessage markup the template used, e.g. {@code "<red>..."}) so the
 * same resolved text is reusable by a future web client, which would parse
 * that markup into HTML/CSS instead of an Adventure {@code Component} - see
 * docs/architecture/web-future.md. Only the in-game frontend (GUI, commands)
 * needs this class.
 *
 * <p><b>Caution with untrusted input:</b> this renders whatever string it's
 * given as MiniMessage markup. Message *templates* are trusted (they ship in
 * {@code lang/*.yml}), but a parameter substituted into a template - e.g. a
 * player's name - is not. A player named {@code <bold>Steve} could inject
 * formatting into a message that echoes their name back. None of the
 * built-in messages do this today; if a future message needs to safely
 * interpolate untrusted text, prefer MiniMessage's own placeholder resolvers
 * ({@code TagResolver}/{@code Placeholder.unparsed}) over string
 * substitution before rendering.
 */
public final class ComponentMessages {

    private ComponentMessages() {
    }

    public static Component render(String resolvedText) {
        return MiniMessage.miniMessage().deserialize(resolvedText);
    }
}

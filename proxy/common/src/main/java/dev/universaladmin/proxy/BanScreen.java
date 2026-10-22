package dev.universaladmin.proxy;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

/**
 * The disconnect screen for a banned player. The texts come from the
 * config (MiniMessage); {@code <reason>}, {@code <expires>} and
 * {@code <remaining>} are inserted as plain text, so a reason can never
 * smuggle formatting or click events into the screen.
 */
public final class BanScreen {

    private final String permanent;
    private final String temporary;
    private final String noReason;
    private final DateTimeFormatter dates;

    public BanScreen(String permanent, String temporary, String noReason, String datePattern) {
        this.permanent = permanent;
        this.temporary = temporary;
        this.noReason = noReason;
        this.dates = DateTimeFormatter.ofPattern(datePattern).withZone(ZoneId.systemDefault());
    }

    public Component render(BanLookup.Ban ban, Instant now) {
        String reason = ban.reason() == null || ban.reason().isBlank() ? noReason : ban.reason();
        if (ban.expiresAt() == null) {
            return MiniMessage.miniMessage().deserialize(permanent, Placeholder.unparsed("reason", reason));
        }
        return MiniMessage.miniMessage().deserialize(temporary, TagResolver.resolver(
                Placeholder.unparsed("reason", reason),
                Placeholder.unparsed("expires", dates.format(ban.expiresAt())),
                Placeholder.unparsed("remaining", remaining(Duration.between(now, ban.expiresAt())))));
    }

    /** "2d 3h", "45m", "30s". */
    static String remaining(Duration duration) {
        long seconds = Math.max(1, duration.getSeconds());
        long days = seconds / 86_400;
        long hours = seconds % 86_400 / 3_600;
        long minutes = seconds % 3_600 / 60;
        if (days > 0) {
            return hours > 0 ? days + "d " + hours + "h" : days + "d";
        }
        if (hours > 0) {
            return minutes > 0 ? hours + "h " + minutes + "m" : hours + "h";
        }
        return minutes > 0 ? minutes + "m" : seconds + "s";
    }
}

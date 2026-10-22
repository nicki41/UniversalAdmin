package dev.universaladmin.proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.time.Instant;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

class BanScreenTest {

    private final BanScreen screen = new BanScreen("Banned: <reason>", "Banned: <reason> until <expires> (<remaining>)",
            "No reason", "yyyy-MM-dd");

    private static String plain(net.kyori.adventure.text.Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    @Test
    void theReasonIsInsertedAsPlainText() {
        // A reason must never be able to add formatting or click events.
        String text = plain(screen.render(new BanLookup.Ban("<red>x <click:run_command:/op me>y", null), Instant.now()));
        assertEquals("Banned: <red>x <click:run_command:/op me>y", text);
    }

    @Test
    void aMissingReasonUsesTheConfiguredText() {
        assertEquals("Banned: No reason", plain(screen.render(new BanLookup.Ban(" ", null), Instant.now())));
    }

    @Test
    void remainingTimeIsShortAndReadable() {
        assertEquals("2d 3h", BanScreen.remaining(Duration.ofHours(51)));
        assertEquals("1h 30m", BanScreen.remaining(Duration.ofMinutes(90)));
        assertEquals("45m", BanScreen.remaining(Duration.ofMinutes(45)));
        assertEquals("30s", BanScreen.remaining(Duration.ofSeconds(30)));
        assertEquals("1s", BanScreen.remaining(Duration.ZERO));
    }
}

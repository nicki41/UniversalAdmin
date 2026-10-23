package dev.universaladmin.modules.moderation.templates;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.universaladmin.status.MuteKind;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** {@code punishment-templates.yml}: the bundled files, and broken entries skipped with a readable problem. */
class PunishmentTemplateLoaderTest {

    @ParameterizedTest
    @ValueSource(strings = {"en_US", "de_DE"})
    void theBundledFilesLoadWithoutProblems(String locale) throws IOException {
        PunishmentTemplateLoader.Result result = PunishmentTemplateLoader.parse(bundled(locale));

        assertTrue(result.valid());
        assertEquals(List.of(), result.problems());
        assertEquals(5, result.templates().size());
        PunishmentTemplate spam = result.templates().get(0);
        assertEquals("spam", spam.id());
        assertEquals(Duration.ofDays(90), spam.window());
        assertEquals(List.of("warn", "mute 1h", "mute 1d", "ban 1w"), spam.steps().stream().map(TemplateStep::toString).toList());
        assertNull(result.templates().get(4).window());
    }

    @Test
    void parsesStepsWindowsAndNames() {
        PunishmentTemplateLoader.Result result = PunishmentTemplateLoader.parse("""
                default-window: 30d
                templates:
                  Toxic:
                    reason: "Toxic behaviour"
                    steps: [kick, mute 2h, mute, ban]
                  flood:
                    name: "Chat flood"
                    reason: "Flooding the chat"
                    window: forever
                    steps: [warn]
                """);

        assertEquals(List.of(), result.problems());
        PunishmentTemplate toxic = result.templates().get(0);
        assertEquals("toxic", toxic.id());
        assertEquals("Toxic", toxic.name());
        assertEquals(Duration.ofDays(30), toxic.window());
        assertEquals(new TemplateStep(TemplateStep.Kind.MUTE, Duration.ofHours(2)), toxic.steps().get(1));
        assertTrue(toxic.steps().get(2).permanent());
        assertEquals("Chat flood", result.templates().get(1).name());
        assertNull(result.templates().get(1).window());
    }

    @Test
    void skipsBrokenTemplatesAndKeepsTheRest() {
        PunishmentTemplateLoader.Result result = PunishmentTemplateLoader.parse("""
                templates:
                  good:
                    reason: "Fine"
                    steps: [warn]
                  noreason:
                    steps: [warn]
                  nosteps:
                    reason: "x"
                  badstep:
                    reason: "x"
                    steps: [warn, jail 1d]
                  baddur:
                    reason: "x"
                    steps: [mute soon]
                  "with space":
                    reason: "x"
                    steps: [warn]
                  badwindow:
                    reason: "x"
                    window: often
                    steps: [warn]
                """);

        assertTrue(result.valid());
        assertEquals(List.of("good", "badwindow"), result.templates().stream().map(PunishmentTemplate::id).toList());
        assertEquals(Duration.ofDays(90), result.templates().get(1).window());
        assertEquals(6, result.problems().size());
        assertTrue(result.problems().stream().anyMatch(problem -> problem.contains("jail")));
    }

    @Test
    void textThatIsNotYamlIsInvalid() {
        PunishmentTemplateLoader.Result result = PunishmentTemplateLoader.parse("templates: [unclosed");

        assertFalse(result.valid());
        assertEquals(1, result.problems().size());
    }

    @Test
    void chatAndVoiceMuteStepsKeepTheirKind() {
        TemplateStep voice = TemplateStep.parse("voicemute 1h");
        TemplateStep chat = TemplateStep.parse("ChatMute");

        assertEquals(TemplateStep.Kind.MUTE, voice.kind());
        assertEquals(MuteKind.VOICE, voice.muteKind());
        assertEquals("voicemute 1h", voice.toString());
        assertEquals(MuteKind.CHAT, chat.muteKind());
        assertTrue(chat.permanent());
        assertEquals(MuteKind.FULL, TemplateStep.parse("mute 1d").muteKind());
        assertEquals(MuteKind.FULL, TemplateStep.parse("ban 1d").muteKind());
    }

    @Test
    void stepsRejectDurationsOnWarnAndKick() {
        assertThrows(IllegalArgumentException.class, () -> TemplateStep.parse("warn 1d"));
        assertThrows(IllegalArgumentException.class, () -> TemplateStep.parse("kick 1h"));
        assertEquals("ban", TemplateStep.parse("BAN permanent").toString());
    }

    private static String bundled(String locale) throws IOException {
        try (InputStream in = PunishmentTemplateLoaderTest.class.getResourceAsStream("/punishment-templates/" + locale + ".yml")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}

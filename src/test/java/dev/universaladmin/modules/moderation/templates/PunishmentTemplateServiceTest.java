package dev.universaladmin.modules.moderation.templates;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.universaladmin.modules.moderation.Punishment;
import dev.universaladmin.modules.moderation.PunishmentType;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

/** Which step a player gets next: what counts as an earlier use of a template. */
class PunishmentTemplateServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-01T12:00:00Z");
    private static final UUID STEVE = UUID.randomUUID();
    private static final PunishmentTemplate SPAM = new PunishmentTemplate("spam", "Spam", "Spam", Duration.ofDays(90),
            List.of(TemplateStep.parse("warn"), TemplateStep.parse("mute 1h"), TemplateStep.parse("ban 7d")));

    @Test
    void theFirstUseGetsTheFirstStep() {
        PunishmentTemplateService.Choice choice = PunishmentTemplateService.choose(SPAM, List.of(), NOW);

        assertEquals(1, choice.stepNumber());
        assertEquals(TemplateStep.Kind.WARN, choice.step().kind());
    }

    @Test
    void earlierUsesWithinTheWindowEscalate() {
        List<Punishment> history = List.of(
                punishment(PunishmentType.TEMP_MUTE, "spam", NOW.minus(Duration.ofDays(2)), false),
                punishment(PunishmentType.WARN, "spam", NOW.minus(Duration.ofDays(30)), false));

        PunishmentTemplateService.Choice choice = PunishmentTemplateService.choose(SPAM, history, NOW);

        assertEquals(2, choice.previousUses());
        assertEquals(3, choice.stepNumber());
        assertEquals(TemplateStep.Kind.BAN, choice.step().kind());
    }

    @Test
    void afterTheLastStepTheLastOneRepeats() {
        List<Punishment> history = List.of(
                punishment(PunishmentType.TEMP_BAN, "spam", NOW.minus(Duration.ofDays(1)), false),
                punishment(PunishmentType.TEMP_BAN, "spam", NOW.minus(Duration.ofDays(2)), false),
                punishment(PunishmentType.TEMP_MUTE, "spam", NOW.minus(Duration.ofDays(3)), false),
                punishment(PunishmentType.WARN, "spam", NOW.minus(Duration.ofDays(4)), false));

        assertEquals(3, PunishmentTemplateService.choose(SPAM, history, NOW).stepNumber());
    }

    @Test
    void oldUsesOtherTemplatesAndRemovedWarningsDoNotCount() {
        List<Punishment> history = List.of(
                punishment(PunishmentType.WARN, "spam", NOW.minus(Duration.ofDays(91)), false),
                punishment(PunishmentType.WARN, "insults", NOW.minus(Duration.ofDays(1)), false),
                punishment(PunishmentType.WARN, "spam", NOW.minus(Duration.ofDays(1)), true));

        assertEquals(0, PunishmentTemplateService.choose(SPAM, history, NOW).previousUses());
    }

    @Test
    void liftedMutesAndBansStillCount() {
        List<Punishment> history = List.of(punishment(PunishmentType.TEMP_MUTE, "spam", NOW.minus(Duration.ofDays(1)), true));

        assertEquals(1, PunishmentTemplateService.choose(SPAM, history, NOW).previousUses());
    }

    @Test
    void withoutAWindowEveryEarlierUseCounts() {
        PunishmentTemplate forever = new PunishmentTemplate("cheating", "Cheating", "Cheating", null,
                List.of(TemplateStep.parse("ban 30d"), TemplateStep.parse("ban")));
        List<Punishment> history = List.of(punishment(PunishmentType.TEMP_BAN, "cheating", NOW.minus(Duration.ofDays(900)), true));

        assertTrue(PunishmentTemplateService.choose(forever, history, NOW).step().permanent());
    }

    @Test
    void anInvalidFileKeepsTheTemplatesLoadedBefore() {
        PunishmentTemplateService service = new PunishmentTemplateService(null, Logger.getAnonymousLogger());
        service.apply(PunishmentTemplateLoader.parse("templates:\n  spam:\n    reason: Spam\n    steps: [warn]\n"), "test");

        service.apply(PunishmentTemplateLoader.parse("templates: [unclosed"), "test");

        assertEquals(1, service.templates().size());
        assertTrue(service.find("SPAM").isPresent());
    }

    private static Punishment punishment(PunishmentType type, String template, Instant createdAt, boolean revoked) {
        return new Punishment(1, type, STEVE, "Steve", null, null, "Mod", "reason", createdAt, null, !revoked,
                revoked ? createdAt.plusSeconds(60) : null, revoked ? "Mod" : null, Map.of(), template);
    }
}

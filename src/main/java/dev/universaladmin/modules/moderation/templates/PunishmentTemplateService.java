package dev.universaladmin.modules.moderation.templates;

import dev.universaladmin.modules.moderation.Punishment;
import dev.universaladmin.modules.moderation.PunishmentService;
import dev.universaladmin.modules.moderation.PunishmentType;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * The punishment templates of {@code punishment-templates.yml} and which step
 * a player gets next.
 *
 * <p>Escalation counts the target's earlier punishments with the same
 * template ({@link Punishment#template()}) issued within the template's
 * window. Lifted bans and mutes still count - they happened; removed
 * warnings do not - removing a warning is how staff take one back. Every
 * server of a network reads the same punishments, so the count is the same
 * everywhere; the file itself is per server.
 */
public final class PunishmentTemplateService {

    /** The step a player gets next from {@code template}. {@code stepNumber} is 1-based. */
    public record Choice(PunishmentTemplate template, TemplateStep step, int stepNumber, int previousUses) {
    }

    private final PunishmentService punishments;
    private final Logger logger;

    private volatile Map<String, PunishmentTemplate> templates = Map.of();

    public PunishmentTemplateService(PunishmentService punishments, Logger logger) {
        this.punishments = punishments;
        this.logger = logger;
    }

    /** Every template, in file order. */
    public List<PunishmentTemplate> templates() {
        return List.copyOf(templates.values());
    }

    /** The template with this id (case-insensitive). */
    public Optional<PunishmentTemplate> find(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(templates.get(id.toLowerCase(Locale.ROOT)));
    }

    /** Reads {@code file} again; problems go to the log, and an unreadable file keeps the templates loaded before. */
    public void reload(Path file) {
        String text;
        try {
            text = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            logger.warning("Could not read " + file.getFileName() + " (" + e.getMessage() + ") - keeping the templates loaded before.");
            return;
        }
        apply(PunishmentTemplateLoader.parse(text), file.getFileName().toString());
    }

    /** Takes over a parsed file - see {@link #reload}. */
    public void apply(PunishmentTemplateLoader.Result result, String source) {
        result.problems().forEach(problem -> logger.warning(source + ": " + problem));
        if (!result.valid()) {
            logger.warning(source + " was not loaded - keeping the templates loaded before.");
            return;
        }
        Map<String, PunishmentTemplate> byId = new LinkedHashMap<>();
        result.templates().forEach(template -> byId.put(template.id(), template));
        templates = Collections.unmodifiableMap(byId);
    }

    /** The step {@code targetId} gets next from {@code template}. */
    public CompletableFuture<Choice> next(UUID targetId, PunishmentTemplate template) {
        return punishments.templateHistory(targetId).thenApply(history -> choose(template, history, Instant.now()));
    }

    /** The next step of every template for {@code targetId}, in file order - for menus and {@code /punish <player>}. */
    public CompletableFuture<List<Choice>> preview(UUID targetId) {
        List<PunishmentTemplate> current = templates();
        return punishments.templateHistory(targetId).thenApply(history -> {
            Instant now = Instant.now();
            return current.stream().map(template -> choose(template, history, now)).toList();
        });
    }

    static Choice choose(PunishmentTemplate template, List<Punishment> history, Instant now) {
        int previous = (int) history.stream().filter(punishment -> counts(template, punishment, now)).count();
        return new Choice(template, template.stepFor(previous), template.stepNumberFor(previous), previous);
    }

    private static boolean counts(PunishmentTemplate template, Punishment punishment, Instant now) {
        if (!template.id().equals(punishment.template())) {
            return false;
        }
        if (template.window() != null && punishment.createdAt().isBefore(now.minus(template.window()))) {
            return false;
        }
        return !(punishment.type() == PunishmentType.WARN && punishment.revokedAt() != null);
    }
}

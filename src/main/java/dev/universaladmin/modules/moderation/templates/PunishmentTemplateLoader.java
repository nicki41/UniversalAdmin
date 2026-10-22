package dev.universaladmin.modules.moderation.templates;

import dev.universaladmin.core.time.DurationParseException;
import dev.universaladmin.core.time.DurationParser;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Reads {@code punishment-templates.yml}. A broken template is skipped with a
 * readable problem (for the log) instead of failing the whole file; only a
 * file that is not YAML at all counts as invalid.
 */
public final class PunishmentTemplateLoader {

    /** How far back earlier uses count when neither the template nor the file says otherwise. */
    public static final Duration DEFAULT_WINDOW = Duration.ofDays(90);

    private static final Pattern ID = Pattern.compile("[a-z0-9_-]{1,64}");

    /**
     * @param valid    {@code false} if the text was not YAML - the caller keeps what it had
     * @param problems one line per skipped template or setting, for the log
     */
    public record Result(boolean valid, List<PunishmentTemplate> templates, List<String> problems) {

        public Result {
            templates = List.copyOf(templates);
            problems = List.copyOf(problems);
        }
    }

    private PunishmentTemplateLoader() {
    }

    public static Result parse(String yaml) {
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.loadFromString(yaml);
        } catch (InvalidConfigurationException e) {
            String message = e.getMessage() == null ? "" : e.getMessage().lines().findFirst().orElse("");
            return new Result(false, List.of(), List.of("not valid YAML: " + message));
        }
        List<String> problems = new ArrayList<>();
        Duration defaultWindow = config.contains("default-window")
                ? window(config.getString("default-window"), DEFAULT_WINDOW, "default-window", problems)
                : DEFAULT_WINDOW;
        ConfigurationSection section = config.getConfigurationSection("templates");
        List<PunishmentTemplate> templates = new ArrayList<>();
        if (section == null) {
            return new Result(true, templates, problems);
        }
        for (String key : section.getKeys(false)) {
            String path = "templates." + key;
            String id = key.toLowerCase(Locale.ROOT);
            ConfigurationSection entry = section.getConfigurationSection(key);
            if (!ID.matcher(id).matches()) {
                problems.add(path + ": a template name may only contain a-z, 0-9, _ and - (at most 64 characters)");
                continue;
            }
            if (entry == null) {
                problems.add(path + ": needs reason and steps");
                continue;
            }
            String reason = entry.getString("reason", "").trim();
            if (reason.isEmpty()) {
                problems.add(path + ": needs a reason");
                continue;
            }
            List<String> rawSteps = entry.getStringList("steps");
            if (rawSteps.isEmpty()) {
                problems.add(path + ": needs at least one step");
                continue;
            }
            List<TemplateStep> steps = new ArrayList<>();
            String stepProblem = null;
            for (int i = 0; i < rawSteps.size() && stepProblem == null; i++) {
                try {
                    steps.add(TemplateStep.parse(rawSteps.get(i)));
                } catch (IllegalArgumentException e) {
                    stepProblem = path + ".steps, step " + (i + 1) + " '" + rawSteps.get(i) + "': " + e.getMessage();
                }
            }
            if (stepProblem != null) {
                problems.add(stepProblem);
                continue;
            }
            Duration window = entry.contains("window")
                    ? window(entry.getString("window"), defaultWindow, path + ".window", problems)
                    : defaultWindow;
            String name = entry.getString("name", key).trim();
            templates.add(new PunishmentTemplate(id, name.isEmpty() ? key : name, reason, window, steps));
        }
        return new Result(true, templates, problems);
    }

    /** {@code 0}, {@code forever} or {@code permanent} = no limit ({@code null}); otherwise a duration. */
    private static Duration window(String raw, Duration fallback, String path, List<String> problems) {
        String value = raw == null ? "" : raw.trim();
        if (value.equals("0") || value.equalsIgnoreCase("forever")) {
            return null;
        }
        try {
            return DurationParser.parse(value).orElse(null);
        } catch (DurationParseException e) {
            problems.add(path + ": invalid duration '" + value + "' - using " + (fallback == null ? "forever" : DurationParser.format(fallback)));
            return fallback;
        }
    }
}

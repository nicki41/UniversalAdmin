package dev.universaladmin.modules.moderation.templates;

import java.time.Duration;
import java.util.List;

/**
 * A punishment template from {@code punishment-templates.yml}: a reason and
 * escalating steps. The n-th use on a player within {@code window} applies
 * the n-th step; after the last step the last one repeats.
 *
 * @param id     the key in the file ({@code spam}) - what {@code /punish} takes and what the punishment row stores
 * @param name   shown in menus and messages
 * @param reason the punishment reason players see
 * @param window how far back earlier uses count, {@code null} = forever
 */
public record PunishmentTemplate(String id, String name, String reason, Duration window, List<TemplateStep> steps) {

    public PunishmentTemplate {
        steps = List.copyOf(steps);
        if (steps.isEmpty()) {
            throw new IllegalArgumentException("A template needs at least one step");
        }
    }

    /** The step for a player with {@code previousUses} earlier uses: 0 = the first step. */
    public TemplateStep stepFor(int previousUses) {
        return steps.get(Math.min(Math.max(previousUses, 0), steps.size() - 1));
    }

    /** 1-based number of {@link #stepFor(int)}. */
    public int stepNumberFor(int previousUses) {
        return Math.min(Math.max(previousUses, 0), steps.size() - 1) + 1;
    }
}

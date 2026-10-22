package dev.universaladmin.modules.moderation.action;

import java.util.UUID;

/**
 * Input of {@link ApplyTemplateAction}.
 *
 * @param template the template id ({@code spam})
 * @param note     optional, appended to the template's reason ({@code null} or blank = none)
 */
public record TemplateInput(UUID targetId, String template, String note) {
}

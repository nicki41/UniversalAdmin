package dev.universaladmin.modules.moderation.templates;

import dev.universaladmin.core.time.DurationParser;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.localization.MessageService;

/** How a template step reads for staff ("Mute 1h", "Permanent ban") - the same in menus, commands and replies. */
public final class TemplateTexts {

    private TemplateTexts() {
    }

    public static String step(MessageService messages, TemplateStep step) {
        return switch (step.kind()) {
            case WARN -> messages.get(MessageKey.of("moderation.template.step.warn"));
            case KICK -> messages.get(MessageKey.of("moderation.template.step.kick"));
            case MUTE -> step.permanent()
                    ? messages.get(MessageKey.of("moderation.template.step.mute-permanent"))
                    : messages.get(MessageKey.of("moderation.template.step.mute"), DurationParser.format(step.duration()));
            case BAN -> step.permanent()
                    ? messages.get(MessageKey.of("moderation.template.step.ban-permanent"))
                    : messages.get(MessageKey.of("moderation.template.step.ban"), DurationParser.format(step.duration()));
        };
    }
}

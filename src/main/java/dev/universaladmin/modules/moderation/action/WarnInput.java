package dev.universaladmin.modules.moderation.action;

import java.util.UUID;

/** @param template the punishment template this warning comes from, {@code null} for a direct warning */
public record WarnInput(UUID targetId, String reason, String template) {

    public WarnInput(UUID targetId, String reason) {
        this(targetId, reason, null);
    }
}

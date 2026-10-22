package dev.universaladmin.modules.moderation.action;

import java.util.UUID;

/** @param template the punishment template this kick comes from, {@code null} for a direct kick */
public record KickInput(UUID targetId, String reason, String template) {

    public KickInput(UUID targetId, String reason) {
        this(targetId, reason, null);
    }
}

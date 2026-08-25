package dev.universaladmin.modules.moderation.action;

import java.util.UUID;

public record WarnInput(UUID targetId, String reason) {
}

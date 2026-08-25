package dev.universaladmin.modules.moderation.action;

import java.util.UUID;

public record FreezeInput(UUID targetId, String reason) {
}

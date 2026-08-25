package dev.universaladmin.modules.moderation.action;

import java.util.UUID;

public record KickInput(UUID targetId, String reason) {
}

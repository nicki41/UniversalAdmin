package dev.universaladmin.modules.players.action;

import java.util.UUID;

/** Input for a single-{@code float}-value Players action (Set XP progress, Set Walk/Fly Speed). */
public record SetFloatValueInput(UUID targetId, float value) {
}

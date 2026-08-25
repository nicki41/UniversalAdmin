package dev.universaladmin.modules.players.action;

import java.util.UUID;

/** Input for a single-{@code double}-value Players action (currently: Set Health). */
public record SetDoubleValueInput(UUID targetId, double value) {
}

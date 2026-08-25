package dev.universaladmin.modules.players.action;

import java.util.UUID;

/** Input for a single-{@code int}-value Players action (Set Food, Set Level). */
public record SetIntValueInput(UUID targetId, int value) {
}

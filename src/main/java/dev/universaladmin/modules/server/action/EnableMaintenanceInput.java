package dev.universaladmin.modules.server.action;

/** {@code reason} is admin-facing only (dashboard/audit). {@code message} overrides the default kick message if non-blank. */
public record EnableMaintenanceInput(String reason, String message, boolean kickNonBypassPlayers) {
}

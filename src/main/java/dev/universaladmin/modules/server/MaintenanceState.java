package dev.universaladmin.modules.server;

import java.time.Instant;
import java.util.Locale;
import java.util.Set;

/**
 * Maintenance mode's persisted state - own domain model rather than a
 * {@link dev.universaladmin.settings.SettingDefinition}, since
 * {@link dev.universaladmin.settings.SettingsService} is read-only at
 * runtime (see {@link MaintenanceService}'s javadoc for why).
 *
 * @param reason         admin-facing only (shown on the dashboard/audit log), never sent to a blocked player
 * @param message        MiniMessage kick message override; blank/{@code null} falls back to
 *                        {@link dev.universaladmin.settings.CoreSettings#MAINTENANCE_KICK_MESSAGE}
 * @param allowedPlayers lower-cased player names allowed to join anyway, in addition to
 *                        {@link ServerPermissions#BYPASS_MAINTENANCE}
 */
public record MaintenanceState(
        boolean enabled, String reason, String message, Set<String> allowedPlayers, Instant updatedAt, String updatedBy) {

    public MaintenanceState {
        allowedPlayers = Set.copyOf(allowedPlayers);
    }

    public static MaintenanceState disabled() {
        return new MaintenanceState(false, null, null, Set.of(), Instant.EPOCH, null);
    }

    public boolean isAllowedByName(String playerName) {
        return allowedPlayers.contains(playerName.toLowerCase(Locale.ROOT));
    }
}

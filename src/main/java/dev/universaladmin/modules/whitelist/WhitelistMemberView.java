package dev.universaladmin.modules.whitelist;

import java.util.Optional;
import java.util.UUID;

/**
 * One row of the Members list - the native whitelist is the source of truth
 * for membership itself; {@code managedEntry} is only ever present when
 * UniversalAdmin created the entry.
 */
public record WhitelistMemberView(UUID playerId, String playerName, boolean online, Optional<WhitelistEntry> managedEntry) {

    public boolean managedByUniversalAdmin() {
        return managedEntry.isPresent();
    }
}

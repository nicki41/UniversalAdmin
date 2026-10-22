package dev.universaladmin.modules.whitelist;

import dev.universaladmin.storage.Repository;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Whitelist metadata of one scope ({@link dev.universaladmin.network.ScopeKeys}):
 * every method reads and writes only the rows of the scope the repository
 * was made for - this server's, or the network's.
 */
public interface WhitelistEntryRepository extends Repository<WhitelistEntry, UUID> {

    /**
     * Copies the rows of {@code fromScope} for {@code playerIds} into this
     * repository's scope, where it has none yet - how a network server takes
     * over its entries from the single shared table used before 1.3.0.
     *
     * @return how many rows were copied
     */
    CompletableFuture<Integer> adopt(String fromScope, Set<UUID> playerIds);
}

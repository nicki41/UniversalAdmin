package dev.universaladmin.modules.players;

import dev.universaladmin.storage.Repository;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface PlayerProfileRepository extends Repository<PlayerProfile, UUID> {

    /**
     * Bounded, filtered, sorted lookup backing the Offline Players/Search/
     * Recently Seen GUI lists - the indexed alternative to loading every
     * profile via {@link #findAll()}. See {@link PlayerSearchQuery}.
     */
    CompletableFuture<List<PlayerProfile>> search(PlayerSearchQuery query);
}

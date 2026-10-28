package dev.universaladmin.modules.nick;

import dev.universaladmin.storage.Repository;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Persisted nicks, keyed by player - shared by every server of a network,
 * so a nick follows its player there. A name is unique across all rows
 * (case-insensitive): {@link #save} fails with {@link NickNameTakenException}
 * when another player already has it.
 */
public interface NickRepository extends Repository<NickRecord, UUID> {

    /** The nick with this name (case-insensitive), whoever has it. */
    CompletableFuture<Optional<NickRecord>> findByName(String name);
}

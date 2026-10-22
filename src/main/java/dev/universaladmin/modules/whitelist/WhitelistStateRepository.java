package dev.universaladmin.modules.whitelist;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * The on/off switch of a whitelist that is not the server's native one - in
 * practice the network whitelist ({@code ua_whitelist_state}, scope
 * {@code network}). A server's own whitelist stays switched by Paper.
 */
public interface WhitelistStateRepository {

    /** Whether the whitelist of {@code scope} is on; empty if it was never switched. */
    CompletableFuture<Optional<Boolean>> load(String scope);

    CompletableFuture<Void> save(String scope, boolean enabled, String updatedBy);
}

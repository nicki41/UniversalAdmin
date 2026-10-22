package dev.universaladmin.modules.server;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Persists one {@link MaintenanceState} per scope key: {@code local} (a
 * server without network mode), {@code server:<name>} (one server of a
 * network) and {@code network} (the whole network) - see
 * {@link DefaultMaintenanceService}. Deliberately not a
 * {@link dev.universaladmin.storage.Repository}: a handful of fixed keys,
 * always read together, never queried.
 */
public interface MaintenanceStateRepository {

    /** The stored states for {@code scopes}; a scope without a row is absent from the map. */
    CompletableFuture<Map<String, MaintenanceState>> load(Set<String> scopes);

    CompletableFuture<Void> save(String scope, MaintenanceState state);
}

package dev.universaladmin.modules.server;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Persists the single, singleton {@link MaintenanceState} row. Deliberately
 * not a {@link dev.universaladmin.storage.Repository} - that interface is
 * shaped for per-entity access by an {@code ID}, and a singleton row has no
 * natural one; see docs/development/adding-module.md for why every other
 * repository in this codebase extends {@code Repository<T, ID>} instead.
 */
public interface MaintenanceStateRepository {

    CompletableFuture<Optional<MaintenanceState>> load();

    CompletableFuture<Void> save(MaintenanceState state);
}

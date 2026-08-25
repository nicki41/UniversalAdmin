package dev.universaladmin.storage;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Standard shape for data access. Every module's repository implements this
 * (or extends it with extra query methods) instead of exposing SQL or a
 * {@code Connection} to its callers - services and actions depend on
 * {@code Repository}, never on JDBC. See docs/architecture/storage.md.
 *
 * <p>All methods are async: implementations run their JDBC calls on the
 * storage executor (see {@link dev.universaladmin.scheduler.TaskScheduler})
 * and must never be called from, or block, the Paper main thread.
 *
 * @param <T>  the entity type (an immutable record)
 * @param <ID> the entity's identifier type
 */
public interface Repository<T, ID> {

    CompletableFuture<Optional<T>> findById(ID id);

    CompletableFuture<List<T>> findAll();

    /** Inserts or updates {@code entity} and returns the persisted value. */
    CompletableFuture<T> save(T entity);

    CompletableFuture<Void> deleteById(ID id);
}

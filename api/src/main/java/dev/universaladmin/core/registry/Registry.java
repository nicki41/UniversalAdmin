package dev.universaladmin.core.registry;

import java.util.Collection;
import java.util.Optional;

/**
 * A registry of values keyed by identifier. This is the shared shape behind
 * every extension point in UniversalAdmin (modules, actions, GUI pages,
 * permissions, audit event types, ...): register once, look up by key,
 * enumerate everything registered so far.
 *
 * @param <K> the key type (typically a typed ID such as {@code ModuleId})
 * @param <V> the registered value type
 */
public interface Registry<K, V> {

    /**
     * Registers a value under the given key.
     *
     * @throws IllegalStateException if the key is already registered
     */
    void register(K key, V value);

    /** Removes a previously registered value, if present. */
    void unregister(K key);

    Optional<V> get(K key);

    boolean contains(K key);

    Collection<V> all();
}

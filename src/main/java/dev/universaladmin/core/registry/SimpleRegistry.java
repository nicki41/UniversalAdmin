package dev.universaladmin.core.registry;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe {@link Registry} backed by a {@link ConcurrentHashMap}.
 *
 * <p>Registration can happen from module {@code onEnable} calls, which are
 * not guaranteed to all run on the main thread in the future (e.g. an
 * extension loaded asynchronously), so this type does not assume single-
 * threaded access.
 */
public final class SimpleRegistry<K, V> implements Registry<K, V> {

    private final Map<K, V> entries = new ConcurrentHashMap<>();

    @Override
    public void register(K key, V value) {
        if (entries.putIfAbsent(key, value) != null) {
            throw new IllegalStateException("Key already registered: " + key);
        }
    }

    @Override
    public void unregister(K key) {
        entries.remove(key);
    }

    @Override
    public Optional<V> get(K key) {
        return Optional.ofNullable(entries.get(key));
    }

    @Override
    public boolean contains(K key) {
        return entries.containsKey(key);
    }

    @Override
    public Collection<V> all() {
        return Collections.unmodifiableCollection(entries.values());
    }
}

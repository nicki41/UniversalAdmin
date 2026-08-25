package dev.universaladmin.core;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cross-module lookup for application services, keyed by type - e.g. the
 * Moderation module looking up {@code PlayerService} that the Players module
 * registered, without constructing its own copy or depending on the Players
 * module's internal wiring.
 *
 * <p>This is for services meant to be shared across modules. A service only
 * one module uses does not need to go through here; it can just be built and
 * held locally in that module's {@code onEnable}.
 */
public final class ServiceRegistry {

    private final Map<Class<?>, Object> services = new ConcurrentHashMap<>();

    public <T> void register(Class<T> type, T instance) {
        if (services.putIfAbsent(type, instance) != null) {
            throw new IllegalStateException("Service already registered: " + type.getName());
        }
    }

    public <T> Optional<T> get(Class<T> type) {
        return Optional.ofNullable(type.cast(services.get(type)));
    }

    public <T> T require(Class<T> type) {
        return get(type).orElseThrow(
                () -> new IllegalStateException("No service registered for " + type.getName()));
    }
}

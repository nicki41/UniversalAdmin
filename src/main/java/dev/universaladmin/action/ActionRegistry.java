package dev.universaladmin.action;

import dev.universaladmin.core.registry.Registry;
import dev.universaladmin.core.registry.SimpleRegistry;
import java.util.Collection;
import java.util.Optional;

/**
 * Registry of every {@link ActionDefinition} known to the platform. Modules
 * register their actions here in {@link dev.universaladmin.module.Module#onEnable};
 * {@link ActionExecutor} looks them up by {@link ActionId} to run them - this
 * registry itself only tracks definitions, it never runs anything.
 */
public final class ActionRegistry {

    private final Registry<ActionId, ActionDefinition<?, ?>> delegate = new SimpleRegistry<>();

    public <I, R> void register(ActionDefinition<I, R> definition) {
        delegate.register(definition.id(), definition);
    }

    /** For a module's disable cleanup - see {@link dev.universaladmin.module.ModuleResources}. */
    public void unregister(ActionId id) {
        delegate.unregister(id);
    }

    @SuppressWarnings("unchecked")
    public <I, R> Optional<ActionDefinition<I, R>> get(ActionId id) {
        return delegate.get(id).map(definition -> (ActionDefinition<I, R>) definition);
    }

    public Collection<ActionDefinition<?, ?>> all() {
        return delegate.all();
    }
}

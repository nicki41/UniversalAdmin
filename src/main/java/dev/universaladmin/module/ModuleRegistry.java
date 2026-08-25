package dev.universaladmin.module;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds every {@link Module} known to the platform and its current
 * {@link ModuleState}. Pure bookkeeping - state *transitions* are driven
 * exclusively by {@link ModuleManager}; this class only stores and reports
 * what {@code ModuleManager} tells it.
 *
 * <p>Lives on {@link dev.universaladmin.core.UniversalAdmin} so anything with
 * a platform reference (a status command, a future GUI module list) can read
 * module state without depending on {@code ModuleManager} itself.
 */
public final class ModuleRegistry {

    // LinkedHashMap: registration order is the deterministic tie-break for
    // dependency-free modules during topological sort in ModuleManager.
    private final Map<ModuleId, Module> modules = new LinkedHashMap<>();
    private final Map<ModuleId, ModuleState> states = new ConcurrentHashMap<>();

    public synchronized void register(Module module) {
        ModuleId id = module.descriptor().id();
        if (modules.containsKey(id)) {
            throw new IllegalStateException("Module already registered: " + id);
        }
        modules.put(id, module);
        states.put(id, ModuleState.DISCOVERED);
    }

    public Optional<Module> get(ModuleId id) {
        return Optional.ofNullable(modules.get(id));
    }

    public ModuleState state(ModuleId id) {
        ModuleState state = states.get(id);
        if (state == null) {
            throw new IllegalArgumentException("Unknown module: " + id);
        }
        return state;
    }

    /** Package-private: only {@link ModuleManager} drives state transitions. */
    void transition(ModuleId id, ModuleState newState) {
        if (!modules.containsKey(id)) {
            throw new IllegalArgumentException("Unknown module: " + id);
        }
        states.put(id, newState);
    }

    /** Every registered module, in registration order. */
    public List<Module> all() {
        return List.copyOf(modules.values());
    }

    /** Every registered module currently in {@code state}, in registration order. */
    public List<Module> withState(ModuleState state) {
        return modules.values().stream()
                .filter(module -> states.get(module.descriptor().id()) == state)
                .toList();
    }
}

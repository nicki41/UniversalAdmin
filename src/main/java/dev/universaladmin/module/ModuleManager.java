package dev.universaladmin.module;

import dev.universaladmin.core.UniversalAdmin;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Drives every registered {@link Module} through {@code DISCOVERED -&gt;
 * LOADED -&gt; ENABLED} (via {@link #loadAll()}/{@link #enableAll()}) and back
 * down to {@code DISABLED} (via {@link #disableAll()}), in dependency order.
 *
 * <p><b>Failure isolation:</b> a module is not a critical core component
 * (see docs/architecture/modules.md for the full critical/non-critical
 * list). If a module's {@code onLoad}/{@code onEnable} throws, or one of its
 * declared {@link ModuleDescriptor#dependencies()} never reached {@code ENABLED},
 * that module - and only that module - is marked {@link ModuleState#FAILED},
 * with the exception logged in full. Every other module still gets a chance
 * to load/enable. This class never swallows an exception silently; every
 * failure is logged with the offending module id before being isolated.
 *
 * <p>A dependency <i>cycle</i> among modules is different: it means the
 * declared module graph itself is broken, which no amount of isolation can
 * route around, so {@link #loadAll()} throws instead of limping onward.
 */
public final class ModuleManager {

    private final UniversalAdmin platform;
    private final ModuleRegistry registry;
    private final Logger logger;
    private final Map<ModuleId, ModuleContext> contexts = new LinkedHashMap<>();
    private final List<ModuleId> enabledOrder = new ArrayList<>();

    public ModuleManager(UniversalAdmin platform) {
        this.platform = platform;
        this.registry = platform.moduleRegistry();
        this.logger = platform.logger();
    }

    /** Registers a module. Must happen before {@link #loadAll()}. */
    public void register(Module module) {
        registry.register(module);
        logger.fine(() -> "Discovered module " + module.descriptor().id());
    }

    /**
     * Runs {@link Module#onLoad} for every {@code DISCOVERED} module, in
     * dependency order. A module whose declared dependency was never
     * registered at all, or whose {@code onLoad} throws, becomes
     * {@link ModuleState#FAILED} and is skipped by {@link #enableAll()}.
     *
     * @throws IllegalStateException if the declared module graph contains a dependency cycle
     */
    public void loadAll() {
        for (Module module : topologicalOrder(registry.all())) {
            ModuleId id = module.descriptor().id();
            Set<ModuleId> unknownDependencies = unknownDependencies(module);
            if (!unknownDependencies.isEmpty()) {
                logger.severe(() -> "Module " + id + " depends on unregistered module(s) "
                        + unknownDependencies + " - marking it FAILED.");
                registry.transition(id, ModuleState.FAILED);
                continue;
            }

            ModuleContext context = ModuleContext.of(id, platform);
            contexts.put(id, context);
            try {
                module.onLoad(context);
                registry.transition(id, ModuleState.LOADED);
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Module " + id + " failed to load - marking it FAILED.", e);
                registry.transition(id, ModuleState.FAILED);
            }
        }
    }

    /**
     * Runs {@link Module#onEnable} for every {@code LOADED} module, in
     * dependency order, skipping (and marking {@link ModuleState#FAILED}) a
     * module whose dependency is not {@code ENABLED} by the time it's this
     * module's turn.
     */
    public void enableAll() {
        for (Module module : topologicalOrder(registry.withState(ModuleState.LOADED))) {
            ModuleId id = module.descriptor().id();
            Set<ModuleId> unsatisfied = unsatisfiedDependencies(module);
            if (!unsatisfied.isEmpty()) {
                logger.severe(() -> "Module " + id + " cannot enable - dependency/dependencies not enabled: "
                        + unsatisfied);
                registry.transition(id, ModuleState.FAILED);
                continue;
            }

            ModuleContext context = contexts.get(id);
            try {
                module.onEnable(context);
                registry.transition(id, ModuleState.ENABLED);
                enabledOrder.add(id);
                logger.info(() -> module.descriptor().displayName() + " module enabled (" + id + ")");
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Module " + id + " failed to enable - marking it FAILED.", e);
                registry.transition(id, ModuleState.FAILED);
                // Release anything the module managed to register before it threw,
                // so a half-failed enable never leaks a listener or task.
                context.resources().closeAll(logger);
            }
        }
    }

    /**
     * Disables every currently {@code ENABLED} module in reverse enable
     * order and releases its {@link ModuleResources}, regardless of whether
     * {@code onDisable} itself throws.
     */
    public void disableAll() {
        List<ModuleId> reverse = new ArrayList<>(enabledOrder);
        Collections.reverse(reverse);
        for (ModuleId id : reverse) {
            Module module = registry.get(id).orElseThrow();
            ModuleContext context = contexts.get(id);
            try {
                module.onDisable(context);
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Module " + id + " threw while disabling - continuing cleanup.", e);
            } finally {
                context.resources().closeAll(logger);
                registry.transition(id, ModuleState.DISABLED);
            }
        }
        enabledOrder.clear();
        contexts.clear();
    }

    private Set<ModuleId> unknownDependencies(Module module) {
        Set<ModuleId> unknown = new LinkedHashSet<>();
        for (ModuleId dependency : module.descriptor().dependencies()) {
            if (registry.get(dependency).isEmpty()) {
                unknown.add(dependency);
            }
        }
        return unknown;
    }

    private Set<ModuleId> unsatisfiedDependencies(Module module) {
        Set<ModuleId> unsatisfied = new LinkedHashSet<>();
        for (ModuleId dependency : module.descriptor().dependencies()) {
            if (registry.get(dependency).isEmpty() || registry.state(dependency) != ModuleState.ENABLED) {
                unsatisfied.add(dependency);
            }
        }
        return unsatisfied;
    }

    /**
     * Orders {@code modules} so every module comes after the modules it
     * depends on (Kahn's algorithm), breaking ties by registration order.
     * Dependencies on a module outside {@code modules} are not ordering
     * constraints here - {@link #unknownDependencies}/{@link #unsatisfiedDependencies}
     * handle those separately, with a per-module FAILED outcome instead of
     * blocking the whole sort.
     */
    private List<Module> topologicalOrder(Collection<Module> modules) {
        Map<ModuleId, Module> byId = new LinkedHashMap<>();
        for (Module module : modules) {
            byId.put(module.descriptor().id(), module);
        }

        Map<ModuleId, Integer> inDegree = new LinkedHashMap<>();
        Map<ModuleId, List<ModuleId>> dependents = new LinkedHashMap<>();
        for (ModuleId id : byId.keySet()) {
            inDegree.put(id, 0);
            dependents.put(id, new ArrayList<>());
        }
        for (Module module : byId.values()) {
            ModuleId id = module.descriptor().id();
            for (ModuleId dependency : module.descriptor().dependencies()) {
                if (!byId.containsKey(dependency)) {
                    continue;
                }
                dependents.get(dependency).add(id);
                inDegree.merge(id, 1, Integer::sum);
            }
        }

        Deque<ModuleId> ready = new ArrayDeque<>();
        for (ModuleId id : byId.keySet()) {
            if (inDegree.get(id) == 0) {
                ready.add(id);
            }
        }

        List<Module> ordered = new ArrayList<>(byId.size());
        while (!ready.isEmpty()) {
            ModuleId id = ready.poll();
            ordered.add(byId.get(id));
            for (ModuleId dependent : dependents.get(id)) {
                if (inDegree.merge(dependent, -1, Integer::sum) == 0) {
                    ready.add(dependent);
                }
            }
        }

        if (ordered.size() != byId.size()) {
            Set<ModuleId> cyclic = new LinkedHashSet<>(byId.keySet());
            ordered.forEach(module -> cyclic.remove(module.descriptor().id()));
            throw new IllegalStateException("Circular module dependency detected among: " + cyclic);
        }
        return ordered;
    }
}

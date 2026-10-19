package dev.universaladmin.module;

/**
 * A self-contained unit of functionality (Players, Moderation, a future
 * "Discord Integration" extension, ...).
 *
 * <p>A module's job is to <b>register</b> things into the shared registries
 * it's handed in {@link ModuleContext} - actions, GUI pages, permissions,
 * audit event types, migrations - and get out of the way. A module must not
 * contain business logic itself; that belongs in the services and actions it
 * registers. See docs/architecture/modules.md.
 *
 * <p>Built-in modules (dev.universaladmin.modules.*) implement this exact
 * interface. Nothing about it is core-only, which is intentional: it is the
 * seam a future external extension will implement too. See
 * docs/architecture/decisions/0005-extension-ready-design.md.
 *
 * <p>{@link ModuleManager} drives every module through the same two-phase
 * startup: {@link #onLoad} for setup that does not depend on any other
 * module, then {@link #onEnable} - only once every declared
 * {@link ModuleDescriptor#dependencies()} is confirmed {@code ENABLED} - for
 * registering into shared registries.
 *
 * <p><b>{@code onLoad} is where a module registers its {@code Migration}(s)</b>
 * (see {@code dev.universaladmin.storage.Migration}) - not {@code onEnable}.
 * {@link ModuleManager#loadAll()} runs {@code onLoad} for every module to
 * completion before {@link ModuleManager#enableAll()} runs {@code onEnable}
 * for any of them, and {@code UniversalAdminPlugin#onEnable} applies every
 * pending migration in between. A module that registers its migration in
 * {@code onEnable} instead risks its own {@code onEnable} building a
 * repository/service that reads its own table before that table exists -
 * this is not theoretical, it was a real bug (see
 * docs/architecture/threading.md). Anything else that doesn't depend on
 * another module being enabled can live here too; {@code onEnable} is still
 * where most modules do most of their work.
 */
public interface Module {

    /** Static metadata (id, name, dependencies, ...) - see {@link ModuleDescriptor}. */
    ModuleDescriptor descriptor();

    /** Setup that does not depend on any other module being enabled - in particular, {@code Migration} registration. See this interface's javadoc. */
    default void onLoad(ModuleContext context) {
        // most modules have nothing to do before dependency resolution
    }

    /** Register this module's services, actions, GUI pages, permissions, migrations. */
    void onEnable(ModuleContext context);

    /**
     * Release resources this module owns directly (e.g. an internal cache).
     * Listeners, scheduler tasks, and closeables registered through
     * {@link ModuleContext#resources()} during {@code onEnable} are released
     * automatically by {@link ModuleManager} - they do not need to be
     * repeated here.
     */
    default void onDisable(ModuleContext context) {
        // most modules have nothing else to clean up
    }
}

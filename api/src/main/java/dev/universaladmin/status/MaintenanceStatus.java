package dev.universaladmin.status;

/**
 * Whether this server is in maintenance mode, for displays. Provided by the
 * Server module through the {@link dev.universaladmin.core.ServiceRegistry};
 * answers from memory, safe on the main thread.
 *
 * @since 1.2
 */
public interface MaintenanceStatus {

    boolean active();
}

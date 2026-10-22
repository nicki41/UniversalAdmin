package dev.universaladmin.modules.server;

/**
 * What a maintenance state applies to. {@link #SERVER} is this server alone
 * (in network mode: only this server of the network); {@link #NETWORK} is
 * every server of the network and - with UniversalAdmin Velocity - the
 * proxy itself. Only {@link #SERVER} exists without network mode.
 */
public enum MaintenanceScope {
    SERVER,
    NETWORK
}

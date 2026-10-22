package dev.universaladmin.modules.whitelist;

/**
 * Which whitelist: {@link #SERVER} is this server's native whitelist (with
 * UniversalAdmin's metadata), {@link #NETWORK} the network whitelist
 * ({@link NetworkWhitelistService}) - only with network mode.
 */
public enum WhitelistScope {
    SERVER,
    NETWORK
}

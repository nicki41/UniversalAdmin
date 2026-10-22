package dev.universaladmin.network;

/**
 * Keys for state that exists once per server or once for the whole network
 * in the shared database - maintenance ({@code ua_maintenance}) and the
 * whitelist ({@code ua_whitelist}): {@link #LOCAL} without network mode,
 * {@code server:<name>} for one server of a network, {@link #NETWORK} for
 * all of them. See ADR 0017.
 */
public final class ScopeKeys {

    /** A server without network mode - its database is its own. */
    public static final String LOCAL = "local";
    /** The whole network. */
    public static final String NETWORK = "network";

    private static final String SERVER_PREFIX = "server:";

    private ScopeKeys() {
    }

    /**
     * This server's own key: {@code server:<name>} in network mode, else
     * {@link #LOCAL}. An unnamed network server's name can change once the
     * proxy tells the real one, so callers ask again instead of keeping it.
     */
    public static String server(NetworkService network) {
        return network.enabled() ? SERVER_PREFIX + network.serverName() : LOCAL;
    }
}

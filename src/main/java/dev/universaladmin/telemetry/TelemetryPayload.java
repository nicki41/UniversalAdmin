package dev.universaladmin.telemetry;

import java.util.Objects;

/**
 * One heartbeat, exactly as it goes over the wire. This record <b>is</b> the
 * contract documented in docs/user/telemetry.md: if a field isn't here, it is
 * not collected, and a field must not be added here without adding it to that
 * document in the same change.
 *
 * <p>{@code pluginId}/{@code pluginVersion} (rather than a UniversalAdmin-
 * specific field name) is the shape a shared, multi-plugin telemetry backend
 * expects - see docs/user/telemetry.md for why. {@link #PLUGIN_ID} is fixed:
 * this class only ever sends heartbeats on behalf of UniversalAdmin itself.
 *
 * <p>What is deliberately <b>not</b> in a heartbeat: server IP, hostname,
 * domain, port, MOTD, world names, coordinates, player names, player UUIDs,
 * player IPs, chat, commands, other installed plugins, file paths, OS user,
 * hardware identifiers. The only identifier is {@link InstallationIdentity},
 * which is random and carries no host information.
 *
 * <p>There is also no client timestamp: the backend stamps arrival time
 * itself, which is what the "active in the last 24 hours" definition is based
 * on (see docs/user/telemetry.md). A client-supplied clock would add a field
 * without adding trustworthy information.
 */
public record TelemetryPayload(
        String pluginId,
        String installationId,
        String pluginVersion,
        String minecraftVersion,
        int javaMajorVersion,
        int onlinePlayers,
        int maxPlayers) {

    /** This plugin's own slug in the shared telemetry wire contract - see docs/user/telemetry.md. */
    public static final String PLUGIN_ID = "universaladmin";

    public TelemetryPayload {
        Objects.requireNonNull(pluginId, "pluginId");
        Objects.requireNonNull(installationId, "installationId");
        Objects.requireNonNull(pluginVersion, "pluginVersion");
        Objects.requireNonNull(minecraftVersion, "minecraftVersion");
    }

    public static TelemetryPayload of(
            InstallationIdentity identity, TelemetryEnvironment environment, PlayerCounts players) {
        return new TelemetryPayload(
                PLUGIN_ID,
                identity.value(),
                environment.universalAdminVersion(),
                environment.minecraftVersion(),
                environment.javaMajorVersion(),
                players.online(),
                players.max());
    }

    /**
     * The JSON request body. Hand-written rather than pulling in a JSON
     * library for seven flat fields - same reasoning as the audit module's
     * metadata codec, see docs/development/architecture-rules.md's
     * "Dependencies" section.
     */
    public String toJson() {
        return "{"
                + quoted("pluginId") + ':' + quoted(pluginId) + ','
                + quoted("installationId") + ':' + quoted(installationId) + ','
                + quoted("pluginVersion") + ':' + quoted(pluginVersion) + ','
                + quoted("minecraftVersion") + ':' + quoted(minecraftVersion) + ','
                + quoted("javaMajorVersion") + ':' + javaMajorVersion + ','
                + quoted("onlinePlayers") + ':' + onlinePlayers + ','
                + quoted("maxPlayers") + ':' + maxPlayers
                + "}";
    }

    /**
     * Escapes the handful of characters JSON requires. The values here are
     * version strings and a hex id, but a server implementation is free to
     * report an odd version string, and that must not be able to produce
     * malformed JSON.
     */
    private static String quoted(String value) {
        StringBuilder out = new StringBuilder(value.length() + 2).append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append("\\u%04x".formatted((int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }
}

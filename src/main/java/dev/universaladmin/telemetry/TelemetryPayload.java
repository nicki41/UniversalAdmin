package dev.universaladmin.telemetry;

import java.util.Objects;

/**
 * One heartbeat, exactly as it goes over the wire. This record <b>is</b> the
 * contract documented in docs/user/telemetry.md: if a field isn't here, it is
 * not collected, and a field must not be added here without adding it to that
 * document in the same change.
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
        String installationId,
        String universalAdminVersion,
        String minecraftVersion,
        int javaMajorVersion,
        int onlinePlayers,
        int maxPlayers) {

    public TelemetryPayload {
        Objects.requireNonNull(installationId, "installationId");
        Objects.requireNonNull(universalAdminVersion, "universalAdminVersion");
        Objects.requireNonNull(minecraftVersion, "minecraftVersion");
    }

    public static TelemetryPayload of(
            InstallationIdentity identity, TelemetryEnvironment environment, PlayerCounts players) {
        return new TelemetryPayload(
                identity.value(),
                environment.universalAdminVersion(),
                environment.minecraftVersion(),
                environment.javaMajorVersion(),
                players.online(),
                players.max());
    }

    /**
     * The JSON request body. Hand-written rather than pulling in a JSON
     * library for six flat fields - same reasoning as the audit module's
     * metadata codec, see docs/development/architecture-rules.md's
     * "Dependencies" section.
     */
    public String toJson() {
        return "{"
                + quoted("installationId") + ':' + quoted(installationId) + ','
                + quoted("universalAdminVersion") + ':' + quoted(universalAdminVersion) + ','
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

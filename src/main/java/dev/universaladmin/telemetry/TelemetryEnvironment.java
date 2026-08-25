package dev.universaladmin.telemetry;

import java.util.Objects;

/**
 * The part of a heartbeat that cannot change while the server runs: which
 * UniversalAdmin build, on which Minecraft version, on which Java feature
 * release. Captured once at startup and reused for every heartbeat.
 *
 * <p>Each field earns its place against exactly one question the project
 * wants to answer (see docs/user/telemetry.md):
 *
 * <ul>
 *   <li>{@code universalAdminVersion} - version distribution, i.e. how many
 *       installations still run an old build when deciding what to support.
 *   <li>{@code minecraftVersion} - which Minecraft versions to keep building
 *       against.
 *   <li>{@code javaMajorVersion} - whether raising the required Java version
 *       would strand installations.
 * </ul>
 *
 * <p>Notably absent, on purpose: the exact Paper build string. It would be a
 * finer-grained fingerprint than {@code minecraftVersion} without answering
 * any of the three questions above.
 */
public record TelemetryEnvironment(String universalAdminVersion, String minecraftVersion, int javaMajorVersion) {

    public TelemetryEnvironment {
        Objects.requireNonNull(universalAdminVersion, "universalAdminVersion");
        Objects.requireNonNull(minecraftVersion, "minecraftVersion");
        if (javaMajorVersion < 1) {
            throw new IllegalArgumentException("javaMajorVersion must be positive (was " + javaMajorVersion + ")");
        }
    }

    /** The Java feature release this JVM runs ({@code 25} for Java 25). */
    public static int currentJavaMajorVersion() {
        return Runtime.version().feature();
    }
}

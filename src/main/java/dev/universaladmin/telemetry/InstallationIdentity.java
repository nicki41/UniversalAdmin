package dev.universaladmin.telemetry;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Objects;

/**
 * The random identifier for <b>one UniversalAdmin installation</b> - the only
 * identifying value telemetry ever transmits (see docs/user/telemetry.md).
 *
 * <p>Deliberately <b>not</b> derived from anything: not the machine, not a MAC
 * address, not an IP, not the server address or port, not a hostname, not a
 * filesystem path, not the OS user. It is 128 bits from {@link SecureRandom}
 * and nothing else, so it carries no information about the host beyond "these
 * two heartbeats came from the same plugin installation". Deleting
 * {@code installation-id.yml} makes the installation indistinguishable from a
 * brand-new one; that is intentional and documented for server owners.
 *
 * <p>Rendered as 32 lowercase hex characters rather than a
 * {@link java.util.UUID} string so it can never be confused with a Minecraft
 * player UUID in a log, a database, or a backend payload.
 */
public record InstallationIdentity(String value) {

    private static final int LENGTH_BYTES = 16;
    private static final SecureRandom RANDOM = new SecureRandom();

    public InstallationIdentity {
        Objects.requireNonNull(value, "value");
        if (!value.matches("[0-9a-f]{32}")) {
            throw new IllegalArgumentException(
                    "An installation id must be 32 lowercase hex characters (was '" + value + "')");
        }
    }

    /** A fresh, cryptographically random identity. Called once per installation, never per restart. */
    public static InstallationIdentity generate() {
        byte[] bytes = new byte[LENGTH_BYTES];
        RANDOM.nextBytes(bytes);
        return new InstallationIdentity(HexFormat.of().formatHex(bytes));
    }

    @Override
    public String toString() {
        return value;
    }
}

package dev.universaladmin.telemetry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The identity is the only identifying value telemetry ever sends, so its
 * shape and its randomness are worth pinning down.
 */
class InstallationIdentityTest {

    @Test
    void generatesA128BitLowercaseHexValue() {
        InstallationIdentity identity = InstallationIdentity.generate();

        assertEquals(32, identity.value().length());
        assertTrue(identity.value().matches("[0-9a-f]{32}"), identity.value());
    }

    @Test
    void generatesADifferentValueEveryTime() {
        Set<String> values = new HashSet<>();
        for (int i = 0; i < 1_000; i++) {
            values.add(InstallationIdentity.generate().value());
        }

        assertEquals(1_000, values.size(), "generate() must not repeat itself");
    }

    @Test
    void rejectsAnythingThatIsNotA128BitHexValue() {
        // Notably: a player-style UUID string is not a valid installation id,
        // so the two can never be confused for one another.
        assertThrows(IllegalArgumentException.class,
                () -> new InstallationIdentity("069a79f4-44e9-4726-a5be-fca90e38aaf5"));
        assertThrows(IllegalArgumentException.class, () -> new InstallationIdentity(""));
        assertThrows(IllegalArgumentException.class, () -> new InstallationIdentity("not-hex"));
        assertThrows(IllegalArgumentException.class,
                () -> new InstallationIdentity("ABCDEF0123456789ABCDEF0123456789"));
    }

    @Test
    void isNotDerivedFromTheHost() {
        // A weak but real regression guard: two identities generated in the
        // same JVM, on the same machine, with the same network interfaces and
        // the same working directory, share nothing.
        String first = InstallationIdentity.generate().value();
        String second = InstallationIdentity.generate().value();

        assertNotEquals(first, second);
    }
}

package dev.universaladmin.modules.moderation.alts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;
import java.net.UnknownHostException;
import org.junit.jupiter.api.Test;

/** What counts as "the same address" for alt detection, and that only a keyed hash comes out. */
class AddressHasherTest {

    private final AddressHasher hasher = new AddressHasher("secret-one");

    @Test
    void theSameIpv4AddressGivesTheSameHashAndNothingReadable() throws UnknownHostException {
        String hash = hasher.hash(InetAddress.getByName("203.0.113.7"));

        assertEquals(hash, hasher.hash(InetAddress.getByName("203.0.113.7")));
        assertNotEquals(hash, hasher.hash(InetAddress.getByName("203.0.113.8")));
        assertTrue(hash.matches("[0-9a-f]{64}"));
        assertTrue(!hash.contains("203"));
    }

    @Test
    void anotherKeyGivesAnotherHash() throws UnknownHostException {
        InetAddress address = InetAddress.getByName("203.0.113.7");

        assertNotEquals(hasher.hash(address), new AddressHasher("secret-two").hash(address));
    }

    @Test
    void ipv6CountsByItsSlash64Network() throws UnknownHostException {
        String home = hasher.hash(InetAddress.getByName("2001:db8:1234:5678::1"));

        assertEquals(home, hasher.hash(InetAddress.getByName("2001:db8:1234:5678:abcd:ef01:2345:6789")));
        assertNotEquals(home, hasher.hash(InetAddress.getByName("2001:db8:1234:5679::1")));
    }

    @Test
    void anIpv4AddressInIpv6FormIsTheIpv4Address() throws UnknownHostException {
        byte[] mapped = new byte[16];
        mapped[10] = (byte) 0xff;
        mapped[11] = (byte) 0xff;
        mapped[12] = (byte) 203;
        mapped[13] = 0;
        mapped[14] = 113;
        mapped[15] = 7;

        assertEquals(hasher.hash(InetAddress.getByName("203.0.113.7")),
                hasher.hash(java.net.Inet6Address.getByAddress(null, mapped, -1)));
    }
}

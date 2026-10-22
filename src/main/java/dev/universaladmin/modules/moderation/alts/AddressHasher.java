package dev.universaladmin.modules.moderation.alts;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Turns a connection address into what alt detection stores: an HMAC-SHA256
 * of the address with a random key, never the address itself.
 *
 * <p>IPv4 addresses are hashed as they are. For IPv6 only the first 64 bits
 * count: providers hand every customer a /64 and devices pick new addresses
 * inside it all the time, so the full address would not match the same
 * household twice. An IPv4 address in IPv6 form ({@code ::ffff:1.2.3.4})
 * counts as the IPv4 address.
 */
public final class AddressHasher {

    private static final String ALGORITHM = "HmacSHA256";

    private final SecretKeySpec key;

    /** @param secret the random key, as stored in {@code ua_alt_key} */
    public AddressHasher(String secret) {
        this.key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM);
    }

    /** 64 hex characters. */
    public String hash(InetAddress address) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            return HexFormat.of().formatHex(mac.doFinal(normalize(address)));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HmacSHA256 is not available", e);
        }
    }

    /** The bytes that identify a connection: the IPv4 address, or the /64 prefix of an IPv6 address. */
    static byte[] normalize(InetAddress address) {
        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address) {
            return bytes;
        }
        if (address instanceof Inet6Address && isMappedIpv4(bytes)) {
            return Arrays.copyOfRange(bytes, 12, 16);
        }
        return Arrays.copyOf(bytes, 8);
    }

    private static boolean isMappedIpv4(byte[] bytes) {
        for (int i = 0; i < 10; i++) {
            if (bytes[i] != 0) {
                return false;
            }
        }
        return bytes[10] == (byte) 0xff && bytes[11] == (byte) 0xff;
    }
}

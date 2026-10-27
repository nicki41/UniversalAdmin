package dev.universaladmin.proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Reading a release's version and comparing it - the network part is the proxy's scheduler calling {@code check()}. */
class ProxyUpdateCheckTest {

    @Test
    void readsTheTagOfARelease() {
        assertEquals(Optional.of("1.6.0"), ProxyUpdateCheck.latestVersion("{\"url\":\"x\",\"tag_name\": \"v1.6.0\",\"name\":\"y\"}"));
        assertEquals(Optional.empty(), ProxyUpdateCheck.latestVersion("{\"message\":\"Not Found\"}"));
    }

    @Test
    void onlyANewerVersionCounts() {
        assertTrue(ProxyUpdateCheck.isNewer("1.6.0", "1.5.0"));
        assertTrue(ProxyUpdateCheck.isNewer("1.10.0", "1.9.3"));
        assertTrue(ProxyUpdateCheck.isNewer("1.6.0", "1.6.0-alpha.2"));
        assertFalse(ProxyUpdateCheck.isNewer("1.5.0", "1.5.0"));
        assertFalse(ProxyUpdateCheck.isNewer("1.4.2", "1.5.0"));
        assertFalse(ProxyUpdateCheck.isNewer("1.6.0-alpha.1", "1.6.0"));
    }
}

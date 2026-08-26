package dev.universaladmin.telemetry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * The payload is the privacy contract in code form (see
 * docs/user/telemetry.md), so these tests are as much about what a heartbeat
 * must <b>not</b> contain as about what it does.
 */
class TelemetryPayloadTest {

    private static final InstallationIdentity IDENTITY =
            new InstallationIdentity("0123456789abcdef0123456789abcdef");
    private static final TelemetryEnvironment ENVIRONMENT =
            new TelemetryEnvironment("0.1.0-alpha", "1.21.4", 25);

    private static final Pattern KEY = Pattern.compile("\"([A-Za-z]+)\":");

    @Test
    void serializesExactlyTheDocumentedFields() {
        TelemetryPayload payload = TelemetryPayload.of(IDENTITY, ENVIRONMENT, new PlayerCounts(17, 100));

        assertEquals(
                "{\"pluginId\":\"universaladmin\","
                        + "\"installationId\":\"0123456789abcdef0123456789abcdef\","
                        + "\"pluginVersion\":\"0.1.0-alpha\","
                        + "\"minecraftVersion\":\"1.21.4\","
                        + "\"javaMajorVersion\":25,"
                        + "\"onlinePlayers\":17,"
                        + "\"maxPlayers\":100}",
                payload.toJson());
    }

    /**
     * Pins the key set itself. A new field can only be added by updating this
     * list - which is the moment to also update docs/user/telemetry.md, since
     * nothing may be collected that isn't documented there.
     */
    @Test
    void sendsNoFieldBeyondTheSevenDocumentedOnes() {
        String json = TelemetryPayload.of(IDENTITY, ENVIRONMENT, new PlayerCounts(17, 100)).toJson();

        List<String> keys = new ArrayList<>();
        Matcher matcher = KEY.matcher(json);
        while (matcher.find()) {
            keys.add(matcher.group(1));
        }

        assertEquals(
                List.of("pluginId", "installationId", "pluginVersion", "minecraftVersion",
                        "javaMajorVersion", "onlinePlayers", "maxPlayers"),
                keys);
    }

    @Test
    void carriesOnlyCountsForPlayers_neverAnIdentity() {
        // The only player information in the record is two ints, so there is no
        // constructor path that could carry a name, a UUID, or an address in.
        String json = TelemetryPayload.of(IDENTITY, ENVIRONMENT, new PlayerCounts(42, 100)).toJson();

        assertTrue(json.contains("\"onlinePlayers\":42"), json);
        assertFalse(json.matches(".*[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-.*"),
                "no UUID-shaped value may appear in a heartbeat: " + json);
        assertFalse(json.contains(UUID.randomUUID().toString()));
        assertFalse(json.matches(".*\\b\\d{1,3}(\\.\\d{1,3}){3}\\b.*"),
                "no IPv4-shaped value may appear in a heartbeat: " + json);
    }

    @Test
    void escapesAnOddVersionStringInsteadOfProducingBrokenJson() {
        TelemetryEnvironment odd = new TelemetryEnvironment("1.0\"-\\weird\n", "1.21.4", 25);

        String json = TelemetryPayload.of(IDENTITY, odd, new PlayerCounts(0, 20)).toJson();

        assertTrue(json.contains("\\\"-\\\\weird\\n"), json);
    }
}

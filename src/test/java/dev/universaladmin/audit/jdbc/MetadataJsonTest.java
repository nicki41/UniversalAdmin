package dev.universaladmin.audit.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Round-trips {@link MetadataJson} - the hand-rolled, dependency-free codec for {@code AuditEvent.metadata()}. */
class MetadataJsonTest {

    @Test
    void encodesAnEmptyMapAsNull() {
        assertNull(MetadataJson.encode(Map.of()));
    }

    @Test
    void decodesNullAndBlankAsAnEmptyMap() {
        assertTrue(MetadataJson.decode(null).isEmpty());
        assertTrue(MetadataJson.decode("").isEmpty());
        assertTrue(MetadataJson.decode("   ").isEmpty());
    }

    @Test
    void roundTripsEveryScalarType() {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("count", 3L);
        metadata.put("ratio", 1.5);
        metadata.put("silent", true);
        metadata.put("loud", false);
        metadata.put("note", "hello world");
        metadata.put("missing", null);

        Map<String, Object> decoded = MetadataJson.decode(MetadataJson.encode(metadata));

        assertEquals(3L, decoded.get("count"));
        assertEquals(1.5, decoded.get("ratio"));
        assertEquals(true, decoded.get("silent"));
        assertEquals(false, decoded.get("loud"));
        assertEquals("hello world", decoded.get("note"));
        assertTrue(decoded.containsKey("missing"));
        assertNull(decoded.get("missing"));
    }

    @Test
    void escapesAndUnescapesSpecialCharactersInStrings() {
        String tricky = "quote\" backslash\\ newline\n tab\t unicode";
        Map<String, Object> metadata = Map.of("value", tricky);

        Map<String, Object> decoded = MetadataJson.decode(MetadataJson.encode(metadata));

        assertEquals(tricky, decoded.get("value"));
    }

    @Test
    void rejectsAnUnsupportedValueType() {
        Map<String, Object> metadata = Map.of("bad", new Object());

        assertThrows(IllegalArgumentException.class, () -> MetadataJson.encode(metadata));
    }
}

package dev.universaladmin.update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** {@link Json} only ever needs to read what the GitHub Releases API actually sends - a real (trimmed) response is the main fixture here. */
class JsonTest {

    @Test
    void parsesFlatScalars() {
        assertEquals("hi", Json.parse("\"hi\""));
        assertEquals(42.0, Json.parse("42"));
        assertEquals(Boolean.TRUE, Json.parse("true"));
        assertEquals(Boolean.FALSE, Json.parse("false"));
        assertNull(Json.parse("null"));
    }

    @Test
    void parsesNestedObjectsAndArrays() {
        Object parsed = Json.parse("""
                {
                  "tag_name": "v0.1.0-alpha.2",
                  "html_url": "https://example.com/releases/v0.1.0-alpha.2",
                  "prerelease": true,
                  "assets": [
                    {"name": "universaladmin-core-0.1.0-alpha.2.jar", "browser_download_url": "https://example.com/a.jar"},
                    {"name": "universaladmin-core-0.1.0-alpha.2.jar.sha256", "browser_download_url": "https://example.com/a.jar.sha256"}
                  ]
                }
                """);

        assertTrue(parsed instanceof Map<?, ?>);
        Map<?, ?> root = (Map<?, ?>) parsed;
        assertEquals("v0.1.0-alpha.2", root.get("tag_name"));
        assertEquals(Boolean.TRUE, root.get("prerelease"));

        List<?> assets = (List<?>) root.get("assets");
        assertEquals(2, assets.size());
        Map<?, ?> firstAsset = (Map<?, ?>) assets.get(0);
        assertEquals("universaladmin-core-0.1.0-alpha.2.jar", firstAsset.get("name"));
    }

    @Test
    void unescapesStrings() {
        assertEquals("a\"b\\c\nd", Json.parse("\"a\\\"b\\\\c\\nd\""));
    }

    @Test
    void rejectsMalformedInput() {
        assertThrows(RuntimeException.class, () -> Json.parse("{not json"));
        assertThrows(RuntimeException.class, () -> Json.parse(""));
    }
}

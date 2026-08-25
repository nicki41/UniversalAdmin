package dev.universaladmin.modules.moderation.jdbc;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Minimal, dependency-free JSON codec for {@code Punishment.metadata()} - a
 * flat {@code Map<String, String>} only. No external JSON library dependency
 * (see docs/development/architecture-rules.md's "Dependencies" section) and never Java serialization -
 * just enough of the JSON grammar to encode and parse this one shape
 * correctly, including string escaping. Deliberately a standalone copy of
 * {@code dev.universaladmin.audit.jdbc.MetadataJson} rather than a shared
 * import: that class is package-private by design (storage-only detail of
 * its own module), and duplicating ~90 lines here beats introducing a
 * cross-module import for it.
 *
 * <p>Storage-only detail: only {@link JdbcPunishmentRepository} uses this,
 * the {@code moderation} domain package never sees JSON text.
 */
final class MetadataJson {

    private MetadataJson() {
    }

    /** {@code null} for an empty map, so the {@code metadata} column stays {@code NULL} rather than {@code "{}"}. */
    static String encode(Map<String, String> metadata) {
        if (metadata.isEmpty()) {
            return null;
        }
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> entry : metadata.entrySet()) {
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append(quote(entry.getKey())).append(':').append(quote(entry.getValue()));
        }
        return json.append('}').toString();
    }

    static Map<String, String> decode(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        return new Parser(json).parseObject();
    }

    private static String quote(String value) {
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

    /** A small recursive-descent parser - only ever needs to understand what {@link #encode} produces. */
    private static final class Parser {

        private final String json;
        private int pos;

        Parser(String json) {
            this.json = json;
        }

        Map<String, String> parseObject() {
            Map<String, String> result = new LinkedHashMap<>();
            skipWhitespace();
            expect('{');
            skipWhitespace();
            if (peek() == '}') {
                pos++;
                return result;
            }
            while (true) {
                skipWhitespace();
                String key = parseString();
                skipWhitespace();
                expect(':');
                skipWhitespace();
                result.put(key, parseString());
                skipWhitespace();
                char next = json.charAt(pos++);
                if (next == '}') {
                    return result;
                }
                if (next != ',') {
                    throw malformed();
                }
            }
        }

        private String parseString() {
            expect('"');
            StringBuilder value = new StringBuilder();
            while (true) {
                char c = json.charAt(pos++);
                if (c == '"') {
                    return value.toString();
                }
                if (c != '\\') {
                    value.append(c);
                    continue;
                }
                char escaped = json.charAt(pos++);
                switch (escaped) {
                    case '"' -> value.append('"');
                    case '\\' -> value.append('\\');
                    case '/' -> value.append('/');
                    case 'n' -> value.append('\n');
                    case 'r' -> value.append('\r');
                    case 't' -> value.append('\t');
                    case 'u' -> {
                        value.append((char) Integer.parseInt(json.substring(pos, pos + 4), 16));
                        pos += 4;
                    }
                    default -> throw malformed();
                }
            }
        }

        private void expect(char c) {
            if (pos >= json.length() || json.charAt(pos) != c) {
                throw malformed();
            }
            pos++;
        }

        private char peek() {
            if (pos >= json.length()) {
                throw malformed();
            }
            return json.charAt(pos);
        }

        private void skipWhitespace() {
            while (pos < json.length() && Character.isWhitespace(json.charAt(pos))) {
                pos++;
            }
        }

        private IllegalArgumentException malformed() {
            return new IllegalArgumentException("Malformed punishment metadata JSON at position " + pos + ": " + json);
        }
    }
}

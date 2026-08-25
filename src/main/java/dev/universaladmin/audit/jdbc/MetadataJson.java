package dev.universaladmin.audit.jdbc;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Minimal, dependency-free JSON codec for {@code AuditEvent.metadata()} - a
 * flat {@code Map<String, Object>} of {@link String}/{@link Number}/
 * {@link Boolean}/{@code null} values only (no nested objects/arrays; audit
 * metadata is small structured extras, not a general document store). No
 * external JSON library dependency (see docs/development/architecture-rules.md's "Dependencies" section)
 * and never Java serialization - just enough of the JSON grammar to encode
 * and parse this one shape correctly, including string escaping.
 *
 * <p>Storage-only detail: only {@link JdbcAuditEventRepository} uses this,
 * the {@code audit} domain package never sees JSON text.
 */
final class MetadataJson {

    private MetadataJson() {
    }

    /** {@code null} for an empty map, so the {@code metadata} column stays {@code NULL} rather than {@code "{}"}. */
    static String encode(Map<String, Object> metadata) {
        if (metadata.isEmpty()) {
            return null;
        }
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : metadata.entrySet()) {
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append(quote(entry.getKey())).append(':').append(encodeValue(entry.getValue()));
        }
        return json.append('}').toString();
    }

    static Map<String, Object> decode(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        return new Parser(json).parseObject();
    }

    private static String encodeValue(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String s) {
            return quote(s);
        }
        if (value instanceof Boolean || value instanceof Number) {
            return value.toString();
        }
        throw new IllegalArgumentException(
                "Unsupported audit metadata value type " + value.getClass() + " - only String/Number/Boolean/null are allowed");
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

        Map<String, Object> parseObject() {
            Map<String, Object> result = new LinkedHashMap<>();
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
                result.put(key, parseValue());
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

        private Object parseValue() {
            char c = peek();
            return switch (c) {
                case '"' -> parseString();
                case 't' -> parseLiteral("true", Boolean.TRUE);
                case 'f' -> parseLiteral("false", Boolean.FALSE);
                case 'n' -> parseLiteral("null", null);
                default -> parseNumber();
            };
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

        private Object parseNumber() {
            int start = pos;
            while (pos < json.length() && "-+.0123456789eE".indexOf(json.charAt(pos)) >= 0) {
                pos++;
            }
            String number = json.substring(start, pos);
            if (number.isEmpty()) {
                throw malformed();
            }
            if (number.indexOf('.') >= 0 || number.indexOf('e') >= 0 || number.indexOf('E') >= 0) {
                return Double.parseDouble(number);
            }
            try {
                return Long.parseLong(number);
            } catch (NumberFormatException e) {
                return Double.parseDouble(number);
            }
        }

        private <T> T parseLiteral(String literal, T value) {
            if (!json.startsWith(literal, pos)) {
                throw malformed();
            }
            pos += literal.length();
            return value;
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
            return new IllegalArgumentException("Malformed audit metadata JSON at position " + pos + ": " + json);
        }
    }
}

package dev.universaladmin.update;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A minimal, dependency-free JSON reader - unlike {@code audit.jdbc.MetadataJson}
 * (which only ever needs to encode/decode one flat shape), the GitHub
 * Releases API response this backs ({@link HttpGitHubReleaseClient}) has
 * nested objects and arrays, so this supports the full JSON value grammar
 * (object/array/string/number/boolean/null) - but read-only: nothing in
 * this plugin ever needs to write GitHub-API-shaped JSON, only read it. No
 * external JSON library dependency, per docs/development/architecture-rules.md's
 * "Dependencies" section.
 *
 * <p>Parses into plain {@link Map}/{@link List}/{@link String}/{@link Double}/
 * {@link Boolean}/{@code null} - callers navigate the result themselves
 * (see {@link HttpGitHubReleaseClient#toRelease}), the same "just enough,
 * no schema binding" shape {@code MetadataJson.decode} already uses.
 */
final class Json {

    private Json() {
    }

    static Object parse(String json) {
        Parser parser = new Parser(json);
        parser.skipWhitespace();
        Object value = parser.parseValue();
        parser.skipWhitespace();
        return value;
    }

    private static final class Parser {

        private final String json;
        private int pos;

        Parser(String json) {
            this.json = json;
        }

        Object parseValue() {
            skipWhitespace();
            char c = peek();
            return switch (c) {
                case '{' -> parseObject();
                case '[' -> parseArray();
                case '"' -> parseString();
                case 't' -> parseLiteral("true", Boolean.TRUE);
                case 'f' -> parseLiteral("false", Boolean.FALSE);
                case 'n' -> parseLiteral("null", null);
                default -> parseNumber();
            };
        }

        private Map<String, Object> parseObject() {
            Map<String, Object> result = new LinkedHashMap<>();
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

        private List<Object> parseArray() {
            List<Object> result = new ArrayList<>();
            expect('[');
            skipWhitespace();
            if (peek() == ']') {
                pos++;
                return result;
            }
            while (true) {
                result.add(parseValue());
                skipWhitespace();
                char next = json.charAt(pos++);
                if (next == ']') {
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
                    case 'b' -> value.append('\b');
                    case 'f' -> value.append('\f');
                    case 'u' -> {
                        value.append((char) Integer.parseInt(json.substring(pos, pos + 4), 16));
                        pos += 4;
                    }
                    default -> throw malformed();
                }
            }
        }

        private Double parseNumber() {
            int start = pos;
            while (pos < json.length() && "-+.0123456789eE".indexOf(json.charAt(pos)) >= 0) {
                pos++;
            }
            String number = json.substring(start, pos);
            if (number.isEmpty()) {
                throw malformed();
            }
            return Double.parseDouble(number);
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

        void skipWhitespace() {
            while (pos < json.length() && Character.isWhitespace(json.charAt(pos))) {
                pos++;
            }
        }

        private IllegalArgumentException malformed() {
            return new IllegalArgumentException("Malformed JSON at position " + pos);
        }
    }
}

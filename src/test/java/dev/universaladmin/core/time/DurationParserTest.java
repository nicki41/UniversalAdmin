package dev.universaladmin.core.time;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DurationParserTest {

    @Test
    void parsesEveryConfiguredPreset() {
        assertEquals(Duration.ofMinutes(10), DurationParser.parse("10m").orElseThrow());
        assertEquals(Duration.ofMinutes(30), DurationParser.parse("30m").orElseThrow());
        assertEquals(Duration.ofHours(1), DurationParser.parse("1h").orElseThrow());
        assertEquals(Duration.ofHours(6), DurationParser.parse("6h").orElseThrow());
        assertEquals(Duration.ofDays(1), DurationParser.parse("1d").orElseThrow());
        assertEquals(Duration.ofDays(3), DurationParser.parse("3d").orElseThrow());
        assertEquals(Duration.ofDays(7), DurationParser.parse("7d").orElseThrow());
        assertEquals(Duration.ofDays(30), DurationParser.parse("30d").orElseThrow());
    }

    @Test
    void permanentAndPermReturnEmpty() {
        assertTrue(DurationParser.parse("permanent").isEmpty());
        assertTrue(DurationParser.parse("perm").isEmpty());
        assertTrue(DurationParser.parse("PERMANENT").isEmpty());
    }

    @Test
    void parsesCompoundDurations() {
        Optional<Duration> parsed = DurationParser.parse("1d12h");
        assertEquals(Duration.ofHours(36), parsed.orElseThrow());

        assertEquals(Duration.ofDays(7).plusHours(2).plusMinutes(30), DurationParser.parse("1w 2h 30m").orElseThrow());
    }

    @Test
    void parsesWeeksAndSeconds() {
        assertEquals(Duration.ofDays(14), DurationParser.parse("2w").orElseThrow());
        assertEquals(Duration.ofSeconds(45), DurationParser.parse("45s").orElseThrow());
    }

    @Test
    void rejectsGarbageInput() {
        assertThrows(DurationParseException.class, () -> DurationParser.parse("banana"));
        assertThrows(DurationParseException.class, () -> DurationParser.parse("10x"));
        assertThrows(DurationParseException.class, () -> DurationParser.parse("10m garbage"));
        assertThrows(DurationParseException.class, () -> DurationParser.parse(""));
        assertThrows(DurationParseException.class, () -> DurationParser.parse("   "));
        assertThrows(DurationParseException.class, () -> DurationParser.parse(null));
    }

    @Test
    void rejectsZeroOrNegativeAmounts() {
        assertThrows(DurationParseException.class, () -> DurationParser.parse("0m"));
    }

    @Test
    void isCaseInsensitiveOnUnits() {
        assertFalse(DurationParser.parse("1D").isEmpty());
        assertEquals(DurationParser.parse("1d"), DurationParser.parse("1D"));
    }

    @org.junit.jupiter.api.Test
    void formatIsTheCompactInverseOfParse() {
        org.junit.jupiter.api.Assertions.assertEquals("1d 12h", DurationParser.format(java.time.Duration.ofHours(36)));
        org.junit.jupiter.api.Assertions.assertEquals("2w 3m 4s", DurationParser.format(java.time.Duration.ofSeconds(2 * 604800 + 184)));
        org.junit.jupiter.api.Assertions.assertEquals("0s", DurationParser.format(java.time.Duration.ofMillis(400)));
        org.junit.jupiter.api.Assertions.assertEquals(java.time.Duration.ofHours(36),
                DurationParser.parse(DurationParser.format(java.time.Duration.ofHours(36))).orElseThrow());
    }
}

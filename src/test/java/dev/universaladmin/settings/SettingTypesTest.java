package dev.universaladmin.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.universaladmin.storage.DatabaseType;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class SettingTypesTest {

    @Test
    void enumOfParsesTheRealDatabaseTypeSetting() throws SettingParseException {
        SettingType<DatabaseType> type = SettingTypes.enumOf(DatabaseType.class);

        assertEquals(DatabaseType.SQLITE, type.parse("sqlite"));
        assertEquals(DatabaseType.MYSQL, type.parse("MySQL"));
    }

    @Test
    void enumOfRejectsAnUnknownDatabaseTypeWithAClearMessage() {
        SettingType<DatabaseType> type = SettingTypes.enumOf(DatabaseType.class);

        SettingParseException exception = assertThrows(SettingParseException.class, () -> type.parse("postgres"));
        assertEquals(true, exception.getMessage().contains("postgres"));
    }

    private enum Sample {
        SQLITE,
        MYSQL,
        NOT_OP
    }

    @Test
    void enumOfParsesCaseInsensitivelyAndAcceptsHyphens() throws SettingParseException {
        SettingType<Sample> type = SettingTypes.enumOf(Sample.class);

        assertEquals(Sample.SQLITE, type.parse("sqlite"));
        assertEquals(Sample.MYSQL, type.parse("MySQL"));
        assertEquals(Sample.NOT_OP, type.parse("not-op"));
    }

    @Test
    void enumOfRejectsUnknownValuesWithAClearMessage() {
        SettingType<Sample> type = SettingTypes.enumOf(Sample.class);

        SettingParseException exception = assertThrows(SettingParseException.class, () -> type.parse("postgres"));
        assertEquals(true, exception.getMessage().contains("postgres"));
        assertEquals(true, exception.getMessage().contains("SQLITE"));
    }

    @Test
    void durationParsesSuffixedValues() throws SettingParseException {
        assertEquals(Duration.ofSeconds(30), SettingTypes.DURATION.parse("30s"));
        assertEquals(Duration.ofMinutes(5), SettingTypes.DURATION.parse("5m"));
        assertEquals(Duration.ofHours(1), SettingTypes.DURATION.parse("1h"));
        assertEquals(Duration.ofDays(2), SettingTypes.DURATION.parse("2d"));
        assertEquals(Duration.ofMillis(250), SettingTypes.DURATION.parse("250ms"));
    }

    @Test
    void durationTreatsABareNumberAsSeconds() throws SettingParseException {
        assertEquals(Duration.ofSeconds(30), SettingTypes.DURATION.parse(30));
        assertEquals(Duration.ofSeconds(30), SettingTypes.DURATION.parse("30"));
    }

    @Test
    void durationRejectsGarbageWithAClearMessage() {
        SettingParseException exception =
                assertThrows(SettingParseException.class, () -> SettingTypes.DURATION.parse("soon"));
        assertEquals(true, exception.getMessage().contains("duration"));
    }
}

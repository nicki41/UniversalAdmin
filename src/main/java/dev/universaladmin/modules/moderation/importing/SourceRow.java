package dev.universaladmin.modules.moderation.importing;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;

/**
 * Reads the current row of a source table tolerantly: a column that does not
 * exist in this version of the source plugin reads as missing instead of
 * failing the import, and booleans stored as numbers or text are accepted.
 */
final class SourceRow {

    private final ResultSet resultSet;
    private final Set<String> columns;

    SourceRow(ResultSet resultSet) throws SQLException {
        this.resultSet = resultSet;
        this.columns = ForeignDatabase.columns(resultSet);
    }

    boolean has(String column) {
        return columns.contains(column.toLowerCase(Locale.ROOT));
    }

    String string(String column) throws SQLException {
        return has(column) ? resultSet.getString(column) : null;
    }

    /** {@code fallback} if the column is missing or NULL. */
    long number(String column, long fallback) throws SQLException {
        if (!has(column)) {
            return fallback;
        }
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? fallback : value;
    }

    boolean flag(String column, boolean fallback) throws SQLException {
        if (!has(column)) {
            return fallback;
        }
        Object value = resultSet.getObject(column);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        String text = value.toString().trim();
        return text.equals("1") || text.equalsIgnoreCase("true") || text.equalsIgnoreCase("yes");
    }

    /** Milliseconds since the epoch, a TIMESTAMP, or missing; values of 0 or below read as missing. */
    Instant instant(String column) throws SQLException {
        if (!has(column)) {
            return null;
        }
        Object value = resultSet.getObject(column);
        if (value == null) {
            return null;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant();
        }
        if (value instanceof java.util.Date date) {
            return date.toInstant();
        }
        if (value instanceof java.time.LocalDateTime local) {
            return local.atZone(java.time.ZoneId.systemDefault()).toInstant();
        }
        if (value instanceof Number number) {
            return number.longValue() <= 0 ? null : Instant.ofEpochMilli(number.longValue());
        }
        try {
            long millis = Long.parseLong(value.toString().trim());
            return millis <= 0 ? null : Instant.ofEpochMilli(millis);
        } catch (NumberFormatException e) {
            Timestamp timestamp = resultSet.getTimestamp(column);
            return timestamp == null ? null : timestamp.toInstant();
        }
    }
}

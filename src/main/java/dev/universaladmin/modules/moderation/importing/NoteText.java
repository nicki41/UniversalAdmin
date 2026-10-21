package dev.universaladmin.modules.moderation.importing;

/**
 * The text of a staff note an import writes, from the language file
 * ({@code moderation.import.note.*}) - plain text, no formatting tags,
 * because notes are shown exactly as stored.
 */
@FunctionalInterface
public interface NoteText {

    String format(String key, Object... args);
}

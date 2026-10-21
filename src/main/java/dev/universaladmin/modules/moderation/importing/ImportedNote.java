package dev.universaladmin.modules.moderation.importing;

import dev.universaladmin.modules.moderation.notes.PlayerNote;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A staff note created by an import - for what the source knows but
 * UniversalAdmin has no punishment type for (an EssentialsX jail, an
 * AdvancedBan note). Same length limits as notes written in game.
 */
public record ImportedNote(String sourceId, UUID targetId, String targetName, String authorName, String text, Instant createdAt) {

    static final int MAX_TEXT = 512;

    public ImportedNote {
        Objects.requireNonNull(sourceId, "sourceId");
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(text, "text");
        targetName = ImportedPunishment.limit(ImportedPunishment.blankToNull(targetName) == null
                ? ImportTargets.UNKNOWN_NAME : targetName.trim(), ImportedPunishment.MAX_NAME);
        authorName = ImportedPunishment.limit(ImportedPunishment.blankToNull(authorName) == null
                ? "CONSOLE" : authorName.trim(), ImportedPunishment.MAX_ACTOR);
        text = ImportedPunishment.limit(text.trim(), MAX_TEXT);
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }

    public PlayerNote toNote() {
        return new PlayerNote(0, targetId, targetName, null, authorName, text, createdAt);
    }
}

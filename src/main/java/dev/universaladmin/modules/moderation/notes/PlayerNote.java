package dev.universaladmin.modules.moderation.notes;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A staff note about a player ("warned verbally about spawn-camping",
 * "suspected alt of X") - visible only to staff with
 * {@code universaladmin.moderation.notes}, never to the player.
 *
 * @param id       database id; {@code 0} until saved
 * @param authorId {@code null} when written from the console
 */
public record PlayerNote(long id, UUID targetId, String targetName, UUID authorId, String authorName, String text, Instant createdAt) {

    public PlayerNote {
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(targetName, "targetName");
        Objects.requireNonNull(authorName, "authorName");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(createdAt, "createdAt");
    }

    public PlayerNote withId(long newId) {
        return new PlayerNote(newId, targetId, targetName, authorId, authorName, text, createdAt);
    }
}

package dev.universaladmin.modules.moderation.action;

import java.util.UUID;

/** {@code targetId} is carried only for the audit target/self-target check - the lookup itself is by {@code warnId}. */
public record RemoveWarnInput(long warnId, UUID targetId) {
}

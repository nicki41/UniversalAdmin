package dev.universaladmin.modules.moderation;

/**
 * Every kind of punishment row stored in the {@code punishments} table.
 * {@code KICK} is included even though it has no lasting effect - it is
 * persisted (immediately inactive, no expiry) purely so it shows up in
 * "Recent Punishments" history. {@code UNBAN}/{@code UNMUTE}/{@code
 * REMOVE_WARN}/{@code UNFREEZE} are not types of their own - they are
 * actions that revoke an existing {@code BAN}/{@code TEMP_BAN}/{@code
 * IP_BAN}, {@code MUTE}/{@code TEMP_MUTE}, {@code WARN}, or {@code FREEZE}
 * row in place (see {@link PunishmentService}).
 *
 * <p>{@code FREEZE} reuses this exact shape (target, actor, reason,
 * active/revocable) rather than getting its own table - permanent-only,
 * like {@code WARN}, revoked via {@code UnfreezeAction}. Adding a value
 * here is a deliberate exhaustiveness trigger: every {@code switch} over
 * this enum elsewhere (no {@code default} branch, by convention) will fail
 * to compile until updated - see {@code PunishmentListPage.materialFor},
 * {@code PunishmentDetailPage.revokeActionFor}, {@code ModeratePlayerPage.apply}.
 */
public enum PunishmentType {
    KICK,
    BAN,
    TEMP_BAN,
    IP_BAN,
    MUTE,
    TEMP_MUTE,
    WARN,
    FREEZE
}

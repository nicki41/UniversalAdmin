package dev.universaladmin.modules.moderation.action;

import dev.universaladmin.action.ActionResult;
import dev.universaladmin.core.time.DurationParser;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.modules.moderation.Punishment;
import java.util.List;

/**
 * The success messages of the moderation actions - what a staff member reads
 * after a ban, mute or warning, from a command or the GUI alike: who, why,
 * how long, and the id {@code /admin history} and {@code /admin unwarn} use.
 */
final class ModerationResults {

    private ModerationResults() {
    }

    /** {@code moderation.result.<type>} with (name, reason, remaining duration, id). */
    static ActionResult<Punishment> issued(Punishment punishment) {
        String key = switch (punishment.type()) {
            case KICK -> "kick";
            case BAN -> "ban";
            case TEMP_BAN -> "tempban";
            case IP_BAN -> punishment.expiresAt() == null ? "ipban" : "ipban-temp";
            case MUTE -> "mute" + kindSuffix(punishment);
            case TEMP_MUTE -> "tempmute" + kindSuffix(punishment);
            case WARN -> "warn";
            case FREEZE -> "freeze";
        };
        Object reason = punishment.reason() == null || punishment.reason().isBlank()
                ? MessageKey.of("common.none") : punishment.reason();
        String remaining = punishment.expiresAt() == null ? "" : DurationParser.formatRemaining(punishment.expiresAt());
        return ActionResult.success(punishment, MessageKey.of("moderation.result." + key),
                punishment.targetLastKnownName(), reason, remaining, Long.toString(punishment.id()));
    }

    /** {@code "-chat"}/{@code "-voice"} for a chat- or voice-only mute, empty for a full one - the key suffix of results and announcements. */
    static String kindSuffix(Punishment punishment) {
        return switch (punishment.muteKind()) {
            case CHAT -> "-chat";
            case VOICE -> "-voice";
            case FULL -> "";
        };
    }

    /** {@code key} with (name, number of lifted punishments). */
    static ActionResult<List<Punishment>> lifted(List<Punishment> revoked, String key) {
        return ActionResult.success(revoked, MessageKey.of(key),
                revoked.get(0).targetLastKnownName(), Integer.toString(revoked.size()));
    }

    /** The coloured ON/OFF label for a toggle's message. */
    static MessageKey status(boolean on) {
        return MessageKey.of(on ? "moderation.gui.status.on" : "moderation.gui.status.off");
    }
}

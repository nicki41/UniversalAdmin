package dev.universaladmin.modules.moderation;

import dev.universaladmin.localization.ComponentMessages;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.localization.MessageService;
import dev.universaladmin.network.NetworkService;
import dev.universaladmin.settings.SettingsService;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Makes a punishment take effect on the punished player, if they are on this
 * server: the ban screen, the kick, the "you are muted" notice, the freeze.
 * Used right after a punishment is issued here, and - in network mode - when
 * another server reports one for a player who is here (see
 * {@link NetworkModerationBridge}).
 *
 * <p>Bans and kicks go through {@link NetworkService#disconnect}: behind a
 * proxy a plain kick would only move the player to the fallback server.
 * Main thread only.
 */
public final class PunishmentEnforcer {

    private final MessageService messages;
    private final SettingsService settings;
    private final NetworkService network;
    private final FreezeRuntimeState freezeState;

    public PunishmentEnforcer(MessageService messages, SettingsService settings, NetworkService network, FreezeRuntimeState freezeState) {
        this.messages = messages;
        this.settings = settings;
        this.network = network;
        this.freezeState = freezeState;
    }

    /** Applies {@code punishment} to its target if they are online here. */
    public void enforce(Punishment punishment) {
        if (punishment.type() == PunishmentType.FREEZE) {
            freezeState.freeze(punishment.targetId(), punishment.actorName(), punishment.createdAt());
        }
        Player target = Bukkit.getPlayer(punishment.targetId());
        if (target == null) {
            return;
        }
        switch (punishment.type()) {
            case KICK -> network.disconnect(target, kickScreen(punishment));
            case BAN, TEMP_BAN, IP_BAN -> network.disconnect(target, banScreen(punishment));
            case MUTE, TEMP_MUTE -> target.sendMessage(muteNotice(punishment));
            case WARN -> target.sendMessage(render("moderation.enforcement.warned", reason(punishment)));
            case FREEZE -> target.sendMessage(render("moderation.enforcement.frozen", reason(punishment)));
        }
    }

    /** Undoes what lifting {@code punishment} undoes for a player here - today: the freeze. */
    public void lift(Punishment punishment) {
        if (punishment.type() == PunishmentType.FREEZE) {
            freezeState.setFrozen(punishment.targetId(), false);
        }
    }

    /** The screen a banned player sees - on the kick and when trying to join. */
    public Component banScreen(Punishment punishment) {
        return punishment.permanent()
                ? render("moderation.enforcement.banned-permanent", reason(punishment))
                : render("moderation.enforcement.banned-temp", reason(punishment), expiry(punishment));
    }

    /** The notice a muted player gets - when muted and on every blocked message; it says where the mute applies. */
    public Component muteNotice(Punishment punishment) {
        String prefix = switch (punishment.muteKind()) {
            case CHAT -> "moderation.enforcement.chat-muted";
            case VOICE -> "moderation.enforcement.voice-muted";
            case FULL -> "moderation.enforcement.muted";
        };
        return punishment.permanent()
                ? render(prefix + "-permanent", reason(punishment))
                : render(prefix + "-temp", reason(punishment), expiry(punishment));
    }

    private Component kickScreen(Punishment punishment) {
        return render("moderation.enforcement.kicked", reason(punishment));
    }

    private String expiry(Punishment punishment) {
        return ModerationFormat.expiry(punishment.expiresAt(), settings, messages);
    }

    private String reason(Punishment punishment) {
        return punishment.reason() == null || punishment.reason().isBlank()
                ? messages.get(MessageKey.of("moderation.enforcement.no-reason"))
                : punishment.reason();
    }

    private Component render(String key, Object... args) {
        return ComponentMessages.render(messages.get(MessageKey.of(key), args));
    }
}

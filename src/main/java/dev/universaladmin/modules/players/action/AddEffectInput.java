package dev.universaladmin.modules.players.action;

import java.util.UUID;
import org.bukkit.potion.PotionEffectType;

/** @param durationTicks positive; @param amplifier 0-based (amplifier 0 = potency level I) */
public record AddEffectInput(UUID targetId, PotionEffectType type, int durationTicks, int amplifier) {
}

package dev.universaladmin.modules.players.action;

import java.util.UUID;
import org.bukkit.potion.PotionEffectType;

public record RemoveEffectInput(UUID targetId, PotionEffectType type) {
}

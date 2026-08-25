package dev.universaladmin.modules.players.action;

import java.util.UUID;
import org.bukkit.GameMode;

public record SetGamemodeInput(UUID targetId, GameMode gamemode) {
}

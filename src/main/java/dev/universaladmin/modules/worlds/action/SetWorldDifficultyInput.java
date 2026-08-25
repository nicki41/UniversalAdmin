package dev.universaladmin.modules.worlds.action;

import org.bukkit.Difficulty;

public record SetWorldDifficultyInput(String worldName, Difficulty difficulty) {
}

package dev.universaladmin.modules.moderation.command;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.universaladmin.command.AdminCommand;
import dev.universaladmin.command.CommandRegistry;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Every Moderation command registers without a label clash - a clash makes
 * the whole module fail to enable on a real server, which no other test
 * would notice (it happened with {@code /punish}, which {@code moderate}
 * used to own).
 */
class ModerationCommandsTest {

    private final List<AdminCommand> commands = ModerationCommands.all(new ModerationCommands.Dependencies(null, null, null, null, null, null));

    @Test
    void namesAndAliasesAreUnique() {
        CommandRegistry registry = new CommandRegistry();

        commands.forEach(command -> assertDoesNotThrow(() -> registry.register(command), command.name()));
    }

    @Test
    void shortcutsAreUnique() {
        List<String> shortcuts = new ArrayList<>();
        commands.forEach(command -> shortcuts.addAll(command.shortcuts()));
        Set<String> unique = new HashSet<>(shortcuts);

        assertEquals(unique.size(), shortcuts.size(), "duplicate shortcut in " + shortcuts);
        assertTrue(unique.contains("punish"));
        assertTrue(unique.contains("chatmute"));
        assertTrue(unique.contains("voicemute"));
    }
}

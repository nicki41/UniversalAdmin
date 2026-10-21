package dev.universaladmin.discord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.universaladmin.permission.PermissionEvaluator;
import dev.universaladmin.permission.PermissionNode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import net.kyori.adventure.text.Component;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class BotLogicTest {

    @Test
    void rolesGrantTheirPatternsOnly() {
        RolePermissions roles = new RolePermissions(Map.of(
                "1", List.of("*"),
                "2", List.of("universaladmin.moderation.*", "universaladmin.chat.staff")));

        PermissionEvaluator admin = roles.forRoles(List.of("1"));
        PermissionEvaluator moderator = roles.forRoles(List.of("2", "99"));
        PermissionEvaluator nobody = roles.forRoles(List.of("99"));

        assertTrue(admin.has(PermissionNode.of("universaladmin.server.shutdown")));
        assertTrue(moderator.has(PermissionNode.of("universaladmin.moderation.ban")));
        assertTrue(moderator.has(PermissionNode.of("universaladmin.moderation")));
        assertTrue(moderator.has(PermissionNode.of("universaladmin.chat.staff")));
        assertFalse(moderator.has(PermissionNode.of("universaladmin.moderationx")));
        assertFalse(moderator.has(PermissionNode.of("universaladmin.server.shutdown")));
        assertFalse(nobody.has(PermissionNode.of("universaladmin.chat.staff")));
    }

    @Test
    void slashCommandsBecomeAdminCommandLines() {
        BotCommands.Definition ban = BotCommands.find("ban").orElseThrow();

        assertEquals("ban Steve 7d griefing at spawn", line(ban, Map.of("player", "Steve", "duration", "7d", "reason", "griefing  at spawn")));
        assertEquals("ban Steve griefing", line(ban, Map.of("player", "Steve", "duration", "permanent", "reason", "griefing")));
        assertEquals("ban Steve", line(ban, Map.of("player", "Steve")));
        assertEquals("history Notch", line(BotCommands.find("history").orElseThrow(), Map.of("player", "Notch")));
        assertEquals("notes Steve add watch him", line(BotCommands.find("ua").orElseThrow(), Map.of("command", " notes  Steve add watch him ")));
    }

    @Test
    void refusesOptionsThatCouldSmuggleArguments() {
        BotCommands.Definition ban = BotCommands.find("ban").orElseThrow();

        assertEquals("discord.bot.invalid-player", invalid(ban, Map.of("player", "Steve confirm")));
        assertEquals("discord.bot.invalid-player", invalid(ban, Map.of("player", "@everyone")));
        assertEquals("discord.bot.invalid-duration", invalid(ban, Map.of("player", "Steve", "duration", "forever and ever")));
        assertEquals("discord.bot.missing-option", invalid(BotCommands.find("warn").orElseThrow(), Map.of("player", "Steve")));
        assertEquals("discord.bot.empty-command", invalid(BotCommands.find("ua").orElseThrow(), Map.of()));
    }

    @Test
    void requiredOptionsComeFirst() {
        List<String> names = BotCommands.ordered(BotCommands.find("ban").orElseThrow()).stream().map(BotCommands.Option::name).toList();
        assertEquals("player", names.get(0));
    }

    @Test
    void repliesAreCleanedForDiscord() {
        String text = ReplyCollector.render(List.of("[UA] Banned Steve for 7d.", "``` sneaky"));

        assertTrue(text.startsWith("```\n"));
        assertTrue(text.contains("Banned Steve for 7d.\n"));
        assertFalse(text.contains("[UA]"));
        assertFalse(text.substring(4, text.length() - 3).contains("```"));

        String longReply = ReplyCollector.render(Collections.nCopies(200, "a line of text that is long enough"));
        assertTrue(longReply.length() <= ReplyCollector.MAX_LENGTH + 10);
    }

    @Test
    void collectsLinesIntoOneReply() throws Exception {
        ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();
        try {
            List<String> outputs = Collections.synchronizedList(new ArrayList<>());
            ReplyCollector collector = new ReplyCollector(timer, outputs::add, "nothing").start();
            collector.accept(Component.text("[UA] first"));
            collector.accept(Component.text("second"));
            timer.schedule(() -> { }, 1500, TimeUnit.MILLISECONDS).get();

            assertEquals(1, outputs.size());
            assertTrue(outputs.get(0).contains("first\nsecond\n"));
        } finally {
            timer.shutdownNow();
        }
    }

    @Test
    void readsBotSettings() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("mode", "Bot");
        yaml.set("bot.token", "abc");
        yaml.set("bot.guild-id", "123456789012345678");
        yaml.set("bot.channels.staff-chat", "222222222222222222");
        yaml.set("bot.roles.333333333333333333", List.of("universaladmin.*"));
        yaml.set("bot.roles.not-an-id", List.of("*"));

        DiscordConfig config = DiscordConfig.from(yaml);

        assertEquals(DiscordConfig.Mode.BOT, config.mode());
        assertTrue(config.bot().configured());
        assertEquals("222222222222222222", config.bot().staffChat());
        assertEquals(Map.of("333333333333333333", List.of("universaladmin.*")), config.bot().roles());
        assertFalse(DiscordConfig.from(new YamlConfiguration()).bot().configured());
        assertEquals(DiscordConfig.Mode.WEBHOOK, DiscordConfig.from(new YamlConfiguration()).mode());
    }

    private static String line(BotCommands.Definition definition, Map<String, String> options) {
        BotCommands.Result result = BotCommands.commandLine(definition, options::get);
        assertNull(result.invalid());
        return result.commandLine();
    }

    private static String invalid(BotCommands.Definition definition, Map<String, String> options) {
        BotCommands.Result result = BotCommands.commandLine(definition, options::get);
        assertNull(result.commandLine());
        return result.invalid().messageKey();
    }
}

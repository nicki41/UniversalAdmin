package dev.universaladmin.discord;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * The extension's own {@code config.yml}, parsed once per (re)load.
 *
 * @param webhookUrl empty when not configured - nothing is sent then
 * @param bot        the bot settings; used when {@code mode: bot}
 */
record DiscordConfig(
        Mode mode,
        String webhookUrl,
        EventFilter filter,
        boolean includeFailures,
        String username,
        String avatarUrl,
        String serverName,
        BotSettings bot) {

    enum Mode {
        /** One-way: admin actions through a webhook URL. */
        WEBHOOK,
        /** A Discord bot: admin actions, two-way staff chat, slash commands, report buttons. */
        BOT
    }

    /**
     * @param guildId      the Discord server the bot works in
     * @param logChannel   where admin actions are posted; empty for none
     * @param staffChat    the channel bridged with the staff chat; empty for none
     * @param reports      where new reports appear with buttons; empty for none
     * @param roles        Discord role id -> UniversalAdmin permission patterns
     * @param activity     shown under the bot's name, {@code {online}} = players online; empty for none
     */
    record BotSettings(String token, String guildId, String logChannel, String staffChat, String reports,
            Map<String, List<String>> roles, String activity) {

        BotSettings {
            roles = Map.copyOf(roles);
        }

        /** A token and a numeric server id - the minimum to start. */
        boolean configured() {
            return !token.isBlank() && guildId.matches("\\d{5,25}");
        }
    }

    static DiscordConfig from(FileConfiguration config) {
        String mode = config.getString("mode", "webhook").trim().toLowerCase(Locale.ROOT);
        return new DiscordConfig(
                mode.equals("bot") ? Mode.BOT : Mode.WEBHOOK,
                config.getString("webhook-url", "").trim(),
                new EventFilter(config.getStringList("events"), config.getStringList("exclude")),
                config.getBoolean("include-failures", false),
                config.getString("username", "UniversalAdmin"),
                config.getString("avatar-url", "").trim(),
                config.getString("server-name", "Minecraft Server"),
                bot(config.getConfigurationSection("bot")));
    }

    private static BotSettings bot(ConfigurationSection section) {
        if (section == null) {
            return new BotSettings("", "", "", "", "", Map.of(), "");
        }
        Map<String, List<String>> roles = new LinkedHashMap<>();
        ConfigurationSection roleSection = section.getConfigurationSection("roles");
        if (roleSection != null) {
            for (String roleId : roleSection.getKeys(false)) {
                if (roleId.matches("\\d{5,25}")) {
                    roles.put(roleId, List.copyOf(roleSection.getStringList(roleId)));
                }
            }
        }
        return new BotSettings(
                section.getString("token", "").trim(),
                section.getString("guild-id", "").trim(),
                section.getString("channels.log", "").trim(),
                section.getString("channels.staff-chat", "").trim(),
                section.getString("channels.reports", "").trim(),
                roles,
                section.getString("activity", "").trim());
    }

    /** A webhook URL that actually points at Discord - anything else is refused rather than posted to. */
    boolean configured() {
        if (webhookUrl.isEmpty()) {
            return false;
        }
        try {
            URI uri = URI.create(webhookUrl);
            String host = uri.getHost() == null ? "" : uri.getHost();
            return "https".equals(uri.getScheme())
                    && (host.equals("discord.com") || host.equals("discordapp.com") || host.endsWith(".discord.com"))
                    && uri.getPath() != null && uri.getPath().startsWith("/api/webhooks/");
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    List<String> events() {
        return filter.include();
    }
}

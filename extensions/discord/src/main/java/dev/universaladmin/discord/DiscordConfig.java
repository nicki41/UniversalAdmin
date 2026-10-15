package dev.universaladmin.discord;

import java.net.URI;
import java.util.List;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * The extension's own {@code config.yml}, parsed once per (re)load.
 *
 * @param webhookUrl empty when not configured - nothing is sent then
 */
record DiscordConfig(
        String webhookUrl,
        EventFilter filter,
        boolean includeFailures,
        String username,
        String avatarUrl,
        String serverName) {

    static DiscordConfig from(FileConfiguration config) {
        return new DiscordConfig(
                config.getString("webhook-url", "").trim(),
                new EventFilter(config.getStringList("events"), config.getStringList("exclude")),
                config.getBoolean("include-failures", false),
                config.getString("username", "UniversalAdmin"),
                config.getString("avatar-url", "").trim(),
                config.getString("server-name", "Minecraft Server"));
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

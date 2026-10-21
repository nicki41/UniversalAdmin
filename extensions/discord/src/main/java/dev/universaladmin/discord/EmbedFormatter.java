package dev.universaladmin.discord;

import dev.universaladmin.audit.AuditEvent;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Turns an {@link AuditEvent} into a Discord webhook payload: one embed with
 * a readable title, the audit summary, staff member / player / reason fields
 * and a colour per kind of action. Mentions are always disabled
 * ({@code allowed_mentions.parse = []}), so a reason containing
 * {@code @everyone} can never ping anyone.
 */
final class EmbedFormatter {

    private static final int RED = 0xE74C3C;
    private static final int DARK_RED = 0x992D22;
    private static final int ORANGE = 0xE67E22;
    private static final int YELLOW = 0xF1C40F;
    private static final int GREEN = 0x2ECC71;
    private static final int BLUE = 0x3498DB;
    private static final int GRAY = 0x95A5A6;

    /** Title and colour for the event types an admin cares about most; anything else gets a generic one. */
    private static final Map<String, Style> STYLES = Map.ofEntries(
            Map.entry("core:moderation.kick", new Style("Kick", YELLOW)),
            Map.entry("core:moderation.ban", new Style("Ban", RED)),
            Map.entry("core:moderation.tempban", new Style("Temporary ban", RED)),
            Map.entry("core:moderation.ipban", new Style("IP ban", DARK_RED)),
            Map.entry("core:moderation.mute", new Style("Mute", ORANGE)),
            Map.entry("core:moderation.tempmute", new Style("Temporary mute", ORANGE)),
            Map.entry("core:moderation.warn", new Style("Warning", YELLOW)),
            Map.entry("core:moderation.freeze", new Style("Freeze", BLUE)),
            Map.entry("core:moderation.unban", new Style("Unban", GREEN)),
            Map.entry("core:moderation.unmute", new Style("Unmute", GREEN)),
            Map.entry("core:moderation.unfreeze", new Style("Unfreeze", GREEN)),
            Map.entry("core:moderation.removewarn", new Style("Warning removed", GREEN)),
            Map.entry("core:reports.create", new Style("New report", ORANGE)),
            Map.entry("core:reports.claim", new Style("Report claimed", BLUE)),
            Map.entry("core:reports.resolve", new Style("Report resolved", GREEN)),
            Map.entry("core:reports.reject", new Style("Report rejected", GRAY)),
            Map.entry("core:server.shutdown", new Style("Server shutdown", DARK_RED)),
            Map.entry("core:server.restart", new Style("Server restart", ORANGE)),
            Map.entry("core:server.maintenance.enable", new Style("Maintenance on", ORANGE)),
            Map.entry("core:server.maintenance.disable", new Style("Maintenance off", GREEN)));

    private EmbedFormatter() {
    }

    /** One embed for an audit event - shared by the webhook ({@link #payload}) and the bot. */
    record Embed(String title, String description, int color, List<Field> fields, String footer, Instant timestamp) {

        record Field(String name, String value, boolean inline) {
        }
    }

    static Embed embed(AuditEvent event, DiscordConfig config) {
        String type = event.type().toString();
        Style style = STYLES.getOrDefault(type, new Style(fallbackTitle(type), BLUE));
        int color = event.success() ? style.color() : GRAY;
        String title = event.success() ? style.title() : style.title() + " (failed)";
        List<Embed.Field> fields = new ArrayList<>();
        fields.add(field("Staff", event.actor().displayName(), true));
        if (event.target() != null) {
            fields.add(field("Target", event.target().displayName(), true));
        }
        if (event.reason() != null && !event.reason().isBlank()) {
            fields.add(field("Reason", event.reason(), false));
        }
        if (event.oldValue() != null || event.newValue() != null) {
            fields.add(field("Change", String.valueOf(event.oldValue()) + " → " + event.newValue(), false));
        }
        return new Embed(limit(title, 256), limit(event.summary(), 4000), color, fields,
                limit(config.serverName() + " • " + type, 2048), event.timestamp());
    }

    static String payload(AuditEvent event, DiscordConfig config) {
        Embed embed = embed(event, config);
        StringBuilder fields = new StringBuilder();
        for (Embed.Field field : embed.fields()) {
            appendField(fields, field.name(), field.value(), field.inline());
        }

        StringBuilder json = new StringBuilder(512);
        json.append('{');
        json.append("\"username\":").append(Json.string(config.username()));
        if (!config.avatarUrl().isEmpty()) {
            json.append(",\"avatar_url\":").append(Json.string(config.avatarUrl()));
        }
        json.append(",\"allowed_mentions\":{\"parse\":[]}");
        json.append(",\"embeds\":[{");
        json.append("\"title\":").append(Json.string(embed.title()));
        json.append(",\"description\":").append(Json.string(embed.description()));
        json.append(",\"color\":").append(embed.color());
        json.append(",\"fields\":[").append(fields).append(']');
        json.append(",\"footer\":{\"text\":").append(Json.string(embed.footer())).append('}');
        json.append(",\"timestamp\":").append(Json.string(DateTimeFormatter.ISO_INSTANT.format(embed.timestamp())));
        json.append("}]}");
        return json.toString();
    }

    /** A plain test message for {@code /admin discord test}. */
    static String testPayload(DiscordConfig config, String sentBy) {
        return "{\"username\":" + Json.string(config.username())
                + (config.avatarUrl().isEmpty() ? "" : ",\"avatar_url\":" + Json.string(config.avatarUrl()))
                + ",\"allowed_mentions\":{\"parse\":[]}"
                + ",\"embeds\":[{\"title\":\"UniversalAdmin is connected\",\"description\":"
                + Json.string("Test message sent by " + sentBy + " from " + config.serverName() + ".")
                + ",\"color\":" + GREEN
                + ",\"timestamp\":" + Json.string(DateTimeFormatter.ISO_INSTANT.format(Instant.now())) + "}]}";
    }

    private static Embed.Field field(String name, String value, boolean inline) {
        return new Embed.Field(name, limit(value == null || value.isBlank() ? "-" : value, 1024), inline);
    }

    private static void appendField(StringBuilder fields, String name, String value, boolean inline) {
        if (!fields.isEmpty()) {
            fields.append(',');
        }
        fields.append("{\"name\":").append(Json.string(name))
                .append(",\"value\":").append(Json.string(limit(value == null || value.isBlank() ? "-" : value, 1024)))
                .append(",\"inline\":").append(inline).append('}');
    }

    /** {@code core:worlds.time.set} → {@code Worlds: time set}. */
    static String fallbackTitle(String type) {
        String name = type.contains(":") ? type.substring(type.indexOf(':') + 1) : type;
        int dot = name.indexOf('.');
        String module = dot < 0 ? name : name.substring(0, dot);
        String rest = dot < 0 ? "" : name.substring(dot + 1).replace('.', ' ').replace('-', ' ');
        String capitalized = module.isEmpty() ? module : module.substring(0, 1).toUpperCase(Locale.ROOT) + module.substring(1);
        return rest.isEmpty() ? capitalized : capitalized + ": " + rest;
    }

    static String limit(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }

    private record Style(String title, int color) {
    }
}

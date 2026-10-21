package dev.universaladmin.discord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.universaladmin.action.ActionTarget;
import dev.universaladmin.action.Actor;
import dev.universaladmin.action.Source;
import dev.universaladmin.audit.AuditEvent;
import dev.universaladmin.audit.AuditEventType;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DiscordFormattingTest {

    private static final DiscordConfig.BotSettings NO_BOT =
            new DiscordConfig.BotSettings("", "", "", "", "", java.util.Map.of(), "");

    private static final DiscordConfig CONFIG = new DiscordConfig(DiscordConfig.Mode.WEBHOOK,
            "https://discord.com/api/webhooks/1/abc",
            new EventFilter(List.of("core:moderation.*", "core:reports.*"), List.of("core:moderation.notes.*")),
            false, "UniversalAdmin", "", "Test Server", NO_BOT);

    @Test
    void filterMatchesIncludesMinusExcludes() {
        EventFilter filter = CONFIG.filter();

        assertTrue(filter.matches("core:moderation.ban"));
        assertTrue(filter.matches("core:reports.create"));
        assertFalse(filter.matches("core:moderation.notes.add"));
        assertFalse(filter.matches("core:worlds.time.set"));
    }

    @Test
    void globTreatsEverythingButTheStarLiterally() {
        assertTrue(EventFilter.glob("core:server.shutdown", "core:server.shutdown"));
        assertFalse(EventFilter.glob("core:server.shutdown", "core:serverXshutdown"));
        assertTrue(EventFilter.glob("*", "anything:at.all"));
    }

    @Test
    void payloadDisablesMentionsAndEscapesUserText() {
        AuditEvent event = AuditEvent.builder(AuditEventType.core("moderation.ban"), Actor.console(), Source.COMMAND,
                        "Banned Steve: \"@everyone\" spam\nline two")
                .target(ActionTarget.player(UUID.randomUUID(), "Steve"))
                .reason("@everyone spam")
                .build();

        String json = EmbedFormatter.payload(event, CONFIG);

        assertTrue(json.contains("\"allowed_mentions\":{\"parse\":[]}"));
        assertTrue(json.contains("\"title\":\"Ban\""));
        assertTrue(json.contains("\\\"@everyone\\\" spam\\nline two"));
        assertTrue(json.contains("\"name\":\"Target\",\"value\":\"Steve\""));
        assertTrue(json.contains("Test Server • core:moderation.ban"));
    }

    @Test
    void unknownTypesGetAReadableFallbackTitle() {
        assertEquals("Worlds: time set", EmbedFormatter.fallbackTitle("core:worlds.time.set"));
        assertEquals("Tickets: close", EmbedFormatter.fallbackTitle("myext:tickets.close"));
    }

    @Test
    void jsonStringEscapesControlCharacters() {
        assertEquals("\"a\\\\b\\u0001\\t\"", Json.string("a\\b\u0001\t"));
    }

    @Test
    void onlyRealDiscordWebhookUrlsCountAsConfigured() {
        assertTrue(CONFIG.configured());
        assertFalse(withUrl("").configured());
        assertFalse(withUrl("http://discord.com/api/webhooks/1/abc").configured());
        assertFalse(withUrl("https://evil.example/api/webhooks/1/abc").configured());
        assertFalse(withUrl("https://discord.com.evil.example/api/webhooks/1").configured());
        assertTrue(withUrl("https://canary.discord.com/api/webhooks/1/abc").configured());
    }

    @Test
    void readsRetryAfterFromARateLimitResponse() {
        assertEquals(1500, WebhookClient.retryAfterMillis("{\"message\": \"You are being rate limited.\", \"retry_after\": 1.5, \"global\": false}"));
        assertEquals(1000, WebhookClient.retryAfterMillis("not json"));
    }

    private static DiscordConfig withUrl(String url) {
        return new DiscordConfig(DiscordConfig.Mode.WEBHOOK, url, CONFIG.filter(), false, "UniversalAdmin", "", "Test Server", NO_BOT);
    }
}

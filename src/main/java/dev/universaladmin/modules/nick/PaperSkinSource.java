package dev.universaladmin.modules.nick;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.universaladmin.scheduler.TaskScheduler;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;

/**
 * Skins of Mojang accounts. Paper's profile lookup
 * ({@code PlayerProfile#complete}) comes first - it is what the server uses
 * for player heads; an offline-mode server (behind a proxy) only resolves
 * players it has seen that way, so Mojang's own API is asked next. Results
 * are kept for an hour (an unknown name for ten minutes), so trying names
 * does not run into Mojang's request limit.
 */
final class PaperSkinSource implements SkinSource {

    private static final Duration KEEP = Duration.ofHours(1);
    private static final Duration KEEP_UNKNOWN = Duration.ofMinutes(10);
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final String PROFILES = "https://api.mojang.com/users/profiles/minecraft/";
    private static final String SESSIONS = "https://sessionserver.mojang.com/session/minecraft/profile/";

    private final TaskScheduler scheduler;
    private final String userAgent;
    private final Logger logger;
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();

    PaperSkinSource(TaskScheduler scheduler, String version, Logger logger) {
        this.scheduler = scheduler;
        this.userAgent = "UniversalAdmin/" + version;
        this.logger = logger;
    }

    @Override
    public CompletableFuture<Optional<NickRecord.Skin>> skinOf(String account) {
        String key = account.toLowerCase(Locale.ROOT);
        Cached cached = cache.get(key);
        if (cached != null && cached.until().isAfter(Instant.now())) {
            return CompletableFuture.completedFuture(cached.skin());
        }
        return scheduler.supplyAsync(() -> {
            Optional<NickRecord.Skin> skin = viaPaper(account).or(() -> viaMojang(account));
            cache.put(key, new Cached(skin, Instant.now().plus(skin.isPresent() ? KEEP : KEEP_UNKNOWN)));
            return skin;
        });
    }

    private static Optional<NickRecord.Skin> viaPaper(String account) {
        PlayerProfile profile = Bukkit.createProfile(account);
        if (!profile.complete(true, true)) {
            return Optional.empty();
        }
        return profile.getProperties().stream()
                .filter(property -> property.getName().equals("textures") && property.isSigned())
                .findFirst()
                .map(textures -> new NickRecord.Skin(profile.getName() != null ? profile.getName() : account,
                        textures.getValue(), textures.getSignature()));
    }

    private Optional<NickRecord.Skin> viaMojang(String account) {
        try {
            Optional<JsonObject> found = get(PROFILES + URLEncoder.encode(account, StandardCharsets.UTF_8));
            if (found.isEmpty()) {
                return Optional.empty();
            }
            String id = found.get().get("id").getAsString();
            String name = found.get().get("name").getAsString();
            Optional<JsonObject> profile = get(SESSIONS + id + "?unsigned=false");
            if (profile.isEmpty() || !profile.get().has("properties")) {
                return Optional.empty();
            }
            for (JsonElement element : profile.get().getAsJsonArray("properties")) {
                JsonObject property = element.getAsJsonObject();
                if (property.get("name").getAsString().equals("textures") && property.has("signature")) {
                    return Optional.of(new NickRecord.Skin(name, property.get("value").getAsString(),
                            property.get("signature").getAsString()));
                }
            }
            return Optional.empty();
        } catch (IOException | RuntimeException e) {
            logger.log(Level.FINE, "Could not look up the skin of " + account, e);
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    /** The JSON at {@code url}; empty for "no such account" (204/404). */
    private Optional<JsonObject> get(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(TIMEOUT).header("User-Agent", userAgent).GET().build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 204 || response.statusCode() == 404) {
            return Optional.empty();
        }
        if (response.statusCode() != 200) {
            throw new IOException("HTTP " + response.statusCode() + " from " + url);
        }
        return Optional.of(JsonParser.parseString(response.body()).getAsJsonObject());
    }

    private record Cached(Optional<NickRecord.Skin> skin, Instant until) {
    }
}

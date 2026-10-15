package dev.universaladmin.discord;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Posts payloads to the webhook one after another on a single background
 * thread - never on the server thread, and slow enough to stay inside
 * Discord's rate limit; a {@code 429} is retried once after the delay
 * Discord asks for. Failures are logged (without the URL, which is a
 * secret) and dropped - logging admin actions to Discord must never affect
 * the server.
 */
final class WebhookClient implements AutoCloseable {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final long MIN_INTERVAL_MILLIS = 400;
    private static final long MAX_RETRY_WAIT_MILLIS = 10_000;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "UniversalAdminDiscord-webhook");
        thread.setDaemon(true);
        return thread;
    });
    private final Logger logger;
    private long lastSentAt;

    WebhookClient(Logger logger) {
        this.logger = logger;
    }

    /** Queues {@code payload}; the future completes with the HTTP status (or -1 if it could not be sent). */
    CompletableFuture<Integer> send(String webhookUrl, String payload) {
        return CompletableFuture.supplyAsync(() -> post(webhookUrl, payload, true), worker);
    }

    private int post(String webhookUrl, String payload, boolean retryOnRateLimit) {
        try {
            long wait = MIN_INTERVAL_MILLIS - (System.currentTimeMillis() - lastSentAt);
            if (wait > 0) {
                Thread.sleep(wait);
            }
            HttpRequest request = HttpRequest.newBuilder(URI.create(webhookUrl))
                    .timeout(TIMEOUT)
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "UniversalAdmin-Discord")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            lastSentAt = System.currentTimeMillis();
            int status = response.statusCode();
            if (status == 429 && retryOnRateLimit) {
                Thread.sleep(Math.min(MAX_RETRY_WAIT_MILLIS, retryAfterMillis(response.body())));
                return post(webhookUrl, payload, false);
            }
            if (status >= 300) {
                logger.warning("Discord rejected a webhook message (HTTP " + status + "). Check webhook-url in config.yml.");
            }
            return status;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return -1;
        } catch (Exception e) {
            logger.warning("Could not reach Discord: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            return -1;
        }
    }

    /** Discord answers a 429 with {@code {"retry_after": 1.5, ...}} (seconds). */
    static long retryAfterMillis(String body) {
        int index = body == null ? -1 : body.indexOf("\"retry_after\"");
        if (index < 0) {
            return 1000;
        }
        int colon = body.indexOf(':', index);
        int end = colon + 1;
        while (end < body.length() && (Character.isDigit(body.charAt(end)) || body.charAt(end) == '.' || body.charAt(end) == ' ')) {
            end++;
        }
        try {
            return (long) (Double.parseDouble(body.substring(colon + 1, end).trim()) * 1000);
        } catch (NumberFormatException | StringIndexOutOfBoundsException e) {
            return 1000;
        }
    }

    @Override
    public void close() {
        worker.shutdown();
        try {
            if (!worker.awaitTermination(5, TimeUnit.SECONDS)) {
                worker.shutdownNow();
            }
        } catch (InterruptedException e) {
            worker.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}

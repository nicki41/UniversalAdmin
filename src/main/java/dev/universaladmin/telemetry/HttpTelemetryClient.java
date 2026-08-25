package dev.universaladmin.telemetry;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;

/**
 * Posts a heartbeat as JSON to a configured endpoint over the JDK's built-in
 * HTTP client - no new dependency (see
 * docs/development/architecture-rules.md's "Dependencies" section).
 *
 * <p>Timeouts are short on purpose. A heartbeat is worthless if it takes long
 * enough to matter, and this runs on a background virtual thread from
 * {@link dev.universaladmin.scheduler.TaskScheduler}, never on the Paper main
 * thread: a hanging endpoint must cost a parked background thread for a few
 * seconds and nothing else.
 *
 * <p>The response body is discarded, never parsed and never logged: the
 * backend has no way to instruct a server through this channel.
 */
public final class HttpTelemetryClient implements TelemetryClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final URI endpoint;
    private final String userAgent;
    private final HttpClient httpClient;

    public HttpTelemetryClient(URI endpoint, String universalAdminVersion) {
        this.endpoint = Objects.requireNonNull(endpoint, "endpoint");
        this.userAgent = "UniversalAdmin/" + Objects.requireNonNull(universalAdminVersion, "universalAdminVersion");
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                // Redirects are not followed: an endpoint that moved should be
                // reconfigured, not chased to a host the server owner never
                // agreed to talk to.
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Override
    public void send(TelemetryPayload payload) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json; charset=utf-8")
                .header("User-Agent", userAgent)
                .POST(HttpRequest.BodyPublishers.ofString(payload.toJson(), StandardCharsets.UTF_8))
                .build();

        HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            throw new IOException("Telemetry endpoint returned HTTP " + status);
        }
    }

    @Override
    public void close() {
        // shutdownNow() rather than close(): plugin disable must not wait on an
        // in-flight statistics request.
        httpClient.shutdownNow();
    }
}

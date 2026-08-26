package dev.universaladmin.update;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Reads {@code GET /repos/{owner}/{repo}/releases/latest} from the public
 * GitHub REST API - unauthenticated (this plugin has no GitHub token to use,
 * and doesn't need one for a public repository's own release list), over
 * the JDK's built-in {@link HttpClient}, same reasoning as {@code
 * telemetry.HttpTelemetryClient}: no new dependency, short timeouts, no
 * redirects followed.
 *
 * <p>GitHub's unauthenticated rate limit (60 requests/hour per IP) is far
 * above anything a periodic check every few hours could ever hit - see
 * {@link UpdateCheckScheduler}.
 */
public final class HttpGitHubReleaseClient implements GitHubReleaseClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final URI endpoint;
    private final String userAgent;
    private final HttpClient httpClient;

    public HttpGitHubReleaseClient(String repositoryOwner, String repositoryName, String universalAdminVersion) {
        this.endpoint = URI.create(
                "https://api.github.com/repos/" + repositoryOwner + "/" + repositoryName + "/releases/latest");
        this.userAgent = "UniversalAdmin/" + Objects.requireNonNull(universalAdminVersion, "universalAdminVersion");
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Override
    public GitHubRelease fetchLatest() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", userAgent)
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            throw new IOException("GitHub releases endpoint returned HTTP " + status);
        }
        return toRelease(response.body());
    }

    /** Package-visible for {@code HttpGitHubReleaseClientTest} - avoids a real HTTP call to exercise the parsing itself. */
    static GitHubRelease toRelease(String body) throws IOException {
        Object parsed;
        try {
            parsed = Json.parse(body);
        } catch (RuntimeException e) {
            throw new IOException("Could not parse the GitHub releases response as JSON: " + e.getMessage(), e);
        }
        if (!(parsed instanceof Map<?, ?> root)) {
            throw new IOException("Unexpected GitHub releases response shape (not a JSON object).");
        }
        String tagName = requireString(root, "tag_name");
        String htmlUrl = requireString(root, "html_url");
        boolean prerelease = Boolean.TRUE.equals(root.get("prerelease"));
        List<ReleaseAsset> assets = parseAssets(root.get("assets"));
        return new GitHubRelease(tagName, htmlUrl, prerelease, assets);
    }

    private static List<ReleaseAsset> parseAssets(Object rawAssets) throws IOException {
        if (!(rawAssets instanceof List<?> list)) {
            throw new IOException("Unexpected GitHub releases response shape (\"assets\" is not an array).");
        }
        List<ReleaseAsset> assets = new ArrayList<>();
        for (Object entry : list) {
            if (!(entry instanceof Map<?, ?> assetMap)) {
                throw new IOException("Unexpected GitHub releases response shape (an asset entry is not an object).");
            }
            assets.add(new ReleaseAsset(requireString(assetMap, "name"), requireString(assetMap, "browser_download_url")));
        }
        return assets;
    }

    private static String requireString(Map<?, ?> map, String key) throws IOException {
        Object value = map.get(key);
        if (!(value instanceof String string)) {
            throw new IOException("Unexpected GitHub releases response shape (\"" + key + "\" is missing or not a string).");
        }
        return string;
    }

    public void close() {
        httpClient.shutdownNow();
    }
}

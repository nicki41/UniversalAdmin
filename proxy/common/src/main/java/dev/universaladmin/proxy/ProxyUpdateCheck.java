package dev.universaladmin.proxy;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tells the proxy console when a newer UniversalAdmin release is out - at
 * startup and every few hours, while an update is pending. Only a notice: a
 * proxy is not swapped while it runs, and Velocity and BungeeCord have no
 * "apply at the next start" folder like Paper's {@code plugins/update/}.
 */
public final class ProxyUpdateCheck {

    /** The first check, once the proxy is up. */
    public static final Duration INITIAL_DELAY = Duration.ofMinutes(1);
    public static final Duration INTERVAL = Duration.ofHours(6);

    static final String REPOSITORY = "universaladmin-mc/UniversalAdmin";
    private static final Pattern TAG = Pattern.compile("\"tag_name\"\\s*:\\s*\"v?([^\"]+)\"");
    private static final Pattern VERSION = Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+)(-.+)?$");

    private final String currentVersion;
    private final String jarPrefix;
    private final ProxyLog log;
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private volatile boolean failureWarned;

    /**
     * @param currentVersion the proxy plugin's version
     * @param jarPrefix      its jar in a release, e.g. {@code "universaladmin-velocity-"}
     */
    public ProxyUpdateCheck(String currentVersion, String jarPrefix, ProxyLog log) {
        this.currentVersion = currentVersion;
        this.jarPrefix = jarPrefix;
        this.log = log;
    }

    /** One check - blocking, call it from the proxy's scheduler. Never throws. */
    public void check() {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.github.com/repos/" + REPOSITORY + "/releases/latest"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "UniversalAdmin-Proxy/" + currentVersion)
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException("GitHub returned HTTP " + response.statusCode());
            }
            failureWarned = false;
            latestVersion(response.body())
                    .filter(latest -> isNewer(latest, currentVersion))
                    .ifPresent(latest -> log.warn("A new UniversalAdmin version is available: " + latest + " (this proxy runs "
                            + currentVersion + "). Replace this plugin's jar with " + jarPrefix + latest + ".jar from "
                            + "https://github.com/" + REPOSITORY + "/releases/tag/v" + latest + " and restart the proxy."));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            if (!failureWarned) {
                failureWarned = true;
                log.warn("Could not check for a new UniversalAdmin version (" + e.getMessage() + "). This does not affect "
                        + "the proxy; set update-check.enabled: false in config.yml to stop trying.");
            }
        }
    }

    /** The {@code tag_name} of a GitHub release, without its leading {@code v}. */
    static Optional<String> latestVersion(String releaseJson) {
        Matcher matcher = TAG.matcher(releaseJson);
        return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    /** Whether {@code candidate} is a newer SemVer version than {@code current}; a release beats its own pre-releases. */
    static boolean isNewer(String candidate, String current) {
        Matcher a = VERSION.matcher(candidate);
        Matcher b = VERSION.matcher(current);
        if (!a.matches() || !b.matches()) {
            return !candidate.equals(current);
        }
        for (int group = 1; group <= 3; group++) {
            int compared = Integer.compare(Integer.parseInt(a.group(group)), Integer.parseInt(b.group(group)));
            if (compared != 0) {
                return compared > 0;
            }
        }
        return a.group(4) == null && b.group(4) != null;
    }
}

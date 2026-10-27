package dev.universaladmin.update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

/** {@link HttpGitHubReleaseClient#toRelease} against real (trimmed) GitHub Releases API response shapes - no network call involved. */
class HttpGitHubReleaseClientTest {

    private static final String SAMPLE_RESPONSE = """
            {
              "tag_name": "v0.1.0-alpha.2",
              "html_url": "https://github.com/universaladmin-mc/UniversalAdmin/releases/tag/v0.1.0-alpha.2",
              "prerelease": true,
              "assets": [
                {
                  "name": "universaladmin-core-0.1.0-alpha.2.jar",
                  "browser_download_url": "https://github.com/universaladmin-mc/UniversalAdmin/releases/download/v0.1.0-alpha.2/universaladmin-core-0.1.0-alpha.2.jar"
                },
                {
                  "name": "universaladmin-core-0.1.0-alpha.2.jar.sha256",
                  "browser_download_url": "https://github.com/universaladmin-mc/UniversalAdmin/releases/download/v0.1.0-alpha.2/universaladmin-core-0.1.0-alpha.2.jar.sha256"
                }
              ]
            }
            """;

    @Test
    void parsesTagVersionHtmlUrlAndPrerelease() throws IOException {
        GitHubRelease release = HttpGitHubReleaseClient.toRelease(SAMPLE_RESPONSE);

        assertEquals("v0.1.0-alpha.2", release.tagName());
        assertEquals("0.1.0-alpha.2", release.version());
        assertEquals("https://github.com/universaladmin-mc/UniversalAdmin/releases/tag/v0.1.0-alpha.2", release.htmlUrl());
        assertTrue(release.prerelease());
    }

    @Test
    void findsTheJarAssetButNotTheChecksumFile() throws IOException {
        GitHubRelease release = HttpGitHubReleaseClient.toRelease(SAMPLE_RESPONSE);

        ReleaseAsset jar = release.jarAsset("universaladmin-core-").orElseThrow();
        assertEquals("universaladmin-core-0.1.0-alpha.2.jar", jar.name());
        assertEquals("universaladmin-core-0.1.0-alpha.2.jar.sha256", release.sha256Of(jar).orElseThrow().name());
    }

    @Test
    void picksThePluginJarByNameNotTheFirstJar() {
        // A real release lists its assets alphabetically: the API's javadoc jar comes first.
        GitHubRelease release = new GitHubRelease("v1.6.0", "https://example.com", false, List.of(
                new ReleaseAsset("universaladmin-api-1.6.0-javadoc.jar", "a"),
                new ReleaseAsset("universaladmin-bungee-1.6.0.jar.sha256", "b"),
                new ReleaseAsset("universaladmin-core-1.6.0.jar", "c"),
                new ReleaseAsset("universaladmin-core-1.6.0.jar.sha256", "d"),
                new ReleaseAsset("universaladmin-discord-1.6.0.jar", "e")));

        ReleaseAsset core = release.jarAsset("universaladmin-core-").orElseThrow();
        assertEquals("c", core.downloadUrl());
        assertEquals("d", release.sha256Of(core).orElseThrow().downloadUrl());
        assertEquals("e", release.jarAsset("universaladmin-discord-").orElseThrow().downloadUrl());
        assertTrue(release.jarAsset("universaladmin-voicechat-").isEmpty());
    }

    @Test
    void treatsAMissingPrereleaseFieldAsFalse() throws IOException {
        String withoutPrerelease = """
                {"tag_name": "v1.0.0", "html_url": "https://example.com", "assets": []}
                """;

        GitHubRelease release = HttpGitHubReleaseClient.toRelease(withoutPrerelease);

        assertFalse(release.prerelease());
        assertTrue(release.jarAsset("universaladmin-core-").isEmpty());
    }

    @Test
    void rejectsAResponseMissingARequiredField() {
        String missingTagName = """
                {"html_url": "https://example.com", "assets": []}
                """;

        assertThrows(IOException.class, () -> HttpGitHubReleaseClient.toRelease(missingTagName));
    }

    @Test
    void rejectsCompletelyMalformedJson() {
        assertThrows(IOException.class, () -> HttpGitHubReleaseClient.toRelease("not json at all"));
    }

    @Test
    void explainsTheFailuresAServerOwnerCanActOn() {
        assertEquals("no release published yet (HTTP 404)", HttpGitHubReleaseClient.failureFor(404));
        assertTrue(HttpGitHubReleaseClient.failureFor(403).contains("request limit"));
        assertTrue(HttpGitHubReleaseClient.failureFor(429).contains("request limit"));
        assertEquals("GitHub releases endpoint returned HTTP 500", HttpGitHubReleaseClient.failureFor(500));
    }
}

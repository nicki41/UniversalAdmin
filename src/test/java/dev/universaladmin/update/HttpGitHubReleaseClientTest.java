package dev.universaladmin.update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import org.junit.jupiter.api.Test;

/** {@link HttpGitHubReleaseClient#toRelease} against real (trimmed) GitHub Releases API response shapes - no network call involved. */
class HttpGitHubReleaseClientTest {

    private static final String SAMPLE_RESPONSE = """
            {
              "tag_name": "v0.1.0-alpha.2",
              "html_url": "https://github.com/nicki41/UniversalAdmin/releases/tag/v0.1.0-alpha.2",
              "prerelease": true,
              "assets": [
                {
                  "name": "universaladmin-core-0.1.0-alpha.2.jar",
                  "browser_download_url": "https://github.com/nicki41/UniversalAdmin/releases/download/v0.1.0-alpha.2/universaladmin-core-0.1.0-alpha.2.jar"
                },
                {
                  "name": "universaladmin-core-0.1.0-alpha.2.jar.sha256",
                  "browser_download_url": "https://github.com/nicki41/UniversalAdmin/releases/download/v0.1.0-alpha.2/universaladmin-core-0.1.0-alpha.2.jar.sha256"
                }
              ]
            }
            """;

    @Test
    void parsesTagVersionHtmlUrlAndPrerelease() throws IOException {
        GitHubRelease release = HttpGitHubReleaseClient.toRelease(SAMPLE_RESPONSE);

        assertEquals("v0.1.0-alpha.2", release.tagName());
        assertEquals("0.1.0-alpha.2", release.version());
        assertEquals("https://github.com/nicki41/UniversalAdmin/releases/tag/v0.1.0-alpha.2", release.htmlUrl());
        assertTrue(release.prerelease());
    }

    @Test
    void findsTheJarAssetButNotTheChecksumFile() throws IOException {
        GitHubRelease release = HttpGitHubReleaseClient.toRelease(SAMPLE_RESPONSE);

        assertTrue(release.jarAsset().isPresent());
        assertEquals("universaladmin-core-0.1.0-alpha.2.jar", release.jarAsset().get().name());
        assertTrue(release.sha256Asset().isPresent());
        assertEquals("universaladmin-core-0.1.0-alpha.2.jar.sha256", release.sha256Asset().get().name());
    }

    @Test
    void treatsAMissingPrereleaseFieldAsFalse() throws IOException {
        String withoutPrerelease = """
                {"tag_name": "v1.0.0", "html_url": "https://example.com", "assets": []}
                """;

        GitHubRelease release = HttpGitHubReleaseClient.toRelease(withoutPrerelease);

        assertFalse(release.prerelease());
        assertTrue(release.jarAsset().isEmpty());
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
}

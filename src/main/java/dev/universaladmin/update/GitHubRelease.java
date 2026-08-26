package dev.universaladmin.update;

import java.util.List;
import java.util.Optional;

/**
 * The handful of fields this plugin actually reads from a GitHub Releases
 * API response - not a full mirror of that API's shape. {@code tagName} is
 * expected to be {@code "v" + <the SemVer version>} (see
 * docs/release/releasing.md) - {@link #version()} strips the leading
 * {@code v}.
 */
public record GitHubRelease(String tagName, String htmlUrl, boolean prerelease, List<ReleaseAsset> assets) {

    public String version() {
        return tagName.startsWith("v") ? tagName.substring(1) : tagName;
    }

    /** The shaded plugin jar - the only asset a release actually needs for {@code /admin update}, never the {@code .sha256} file itself. */
    public Optional<ReleaseAsset> jarAsset() {
        return assets.stream().filter(asset -> asset.name().endsWith(".jar")).findFirst();
    }

    /** The matching SHA-256 checksum file, if the release published one - see {@code UpdateDownloader}. */
    public Optional<ReleaseAsset> sha256Asset() {
        return assets.stream().filter(asset -> asset.name().endsWith(".jar.sha256")).findFirst();
    }
}

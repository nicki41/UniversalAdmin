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

    /**
     * The plugin jar named {@code prefix + version + ".jar"}. Matched by name,
     * never "the first jar": a UniversalAdmin release also carries the API,
     * its sources and javadoc, and the extensions.
     */
    public Optional<ReleaseAsset> jarAsset(String prefix) {
        String name = prefix + version() + ".jar";
        return assets.stream().filter(asset -> asset.name().equals(name)).findFirst();
    }

    /** The checksum file published next to {@code jar} ({@code <jar>.sha256}), if any - see {@code release.yml}. */
    public Optional<ReleaseAsset> sha256Of(ReleaseAsset jar) {
        String name = jar.name() + ".sha256";
        return assets.stream().filter(asset -> asset.name().equals(name)).findFirst();
    }
}

package dev.universaladmin.update;

/** One downloadable file attached to a {@link GitHubRelease}. */
public record ReleaseAsset(String name, String downloadUrl) {
}

package dev.universaladmin.update;

import java.nio.file.Path;
import java.util.Objects;

/**
 * One plugin the update check covers: UniversalAdmin itself, or an extension
 * that registered through
 * {@link dev.universaladmin.extension.ExtensionManager#updatesFrom}.
 *
 * @param name           the plugin's name, as staff and the console see it
 * @param repository     {@code "owner/name"} on GitHub
 * @param assetPrefix    a release's plugin jar is {@code assetPrefix + version + ".jar"} - a release can carry
 *                       several jars (API, sources, extensions), so the name decides
 * @param currentVersion the version running now
 * @param jarFile        the running jar - a downloaded update is staged under its file name
 */
public record UpdateTarget(String name, String repository, String assetPrefix, String currentVersion, Path jarFile) {

    public UpdateTarget {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(repository, "repository");
        Objects.requireNonNull(assetPrefix, "assetPrefix");
        Objects.requireNonNull(currentVersion, "currentVersion");
        Objects.requireNonNull(jarFile, "jarFile");
        if (repository.split("/").length != 2) {
            throw new IllegalArgumentException("A repository is \"owner/name\": " + repository);
        }
    }
}

package dev.universaladmin.update;

import java.io.IOException;

/**
 * Puts a release's plugin jar where the server applies it at the next start
 * - {@link UpdateDownloader} in production, a fake in tests.
 */
@FunctionalInterface
public interface UpdateStager {

    /**
     * Downloads {@code target}'s jar from {@code release} and stages it.
     *
     * @throws IOException if the release has no matching jar, the download fails or its checksum does not match -
     *                     nothing is staged then
     */
    void stage(UpdateTarget target, GitHubRelease release) throws IOException, InterruptedException;
}

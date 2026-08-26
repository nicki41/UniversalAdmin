package dev.universaladmin.update;

import java.io.IOException;

/** Fetches the latest published GitHub release for this project - see {@link HttpGitHubReleaseClient}. */
public interface GitHubReleaseClient {

    /** Blocking - callers run this off the main thread (see {@link UpdateCheckService}). */
    GitHubRelease fetchLatest() throws IOException, InterruptedException;
}

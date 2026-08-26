package dev.universaladmin.update;

import dev.universaladmin.action.Action;
import dev.universaladmin.action.ActionContext;
import dev.universaladmin.action.ActionId;
import dev.universaladmin.action.ActionResult;
import dev.universaladmin.scheduler.TaskScheduler;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import org.bukkit.Bukkit;

/**
 * Backs {@code /admin update} - downloads the latest GitHub release's jar
 * into Bukkit's own {@code plugins/update/} folder under the exact filename
 * of the jar currently running, so Bukkit's own plugin loader swaps it in
 * at the next server start. Deliberately not a live hot-swap: replacing the
 * running plugin's own classes while it executes is a much larger, far more
 * fragile problem this project has no reason to solve when Bukkit already
 * has a safe, built-in mechanism for "apply on next restart" - see
 * {@link Bukkit#getUpdateFolderFile()}.
 *
 * <p>Verifies the downloaded jar's SHA-256 against the release's {@code
 * .sha256} asset (see {@code release.yml}, which always publishes one)
 * before writing anything to {@code plugins/update/} - a corrupted or
 * incomplete download is refused, not staged.
 */
public final class ApplyUpdateAction implements Action<Void, UpdateApplyResult> {

    public static final ActionId ID = ActionId.core("update.apply");

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration DOWNLOAD_TIMEOUT = Duration.ofMinutes(2);

    private final TaskScheduler scheduler;
    private final GitHubReleaseClient releaseClient;
    private final Supplier<Path> currentJarFile;
    private final String currentVersion;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /** @param currentJarFile the plugin's own currently-running jar path - {@code JavaPlugin#getFile()}, supplied by the caller since it's a protected method not reachable from this package */
    public ApplyUpdateAction(TaskScheduler scheduler, GitHubReleaseClient releaseClient, Supplier<Path> currentJarFile, String currentVersion) {
        this.scheduler = scheduler;
        this.releaseClient = releaseClient;
        this.currentJarFile = currentJarFile;
        this.currentVersion = currentVersion;
    }

    @Override
    public ActionId id() {
        return ID;
    }

    @Override
    public CompletableFuture<ActionResult<UpdateApplyResult>> execute(ActionContext context, Void input) {
        return scheduler.supplyAsync(() -> {
            try {
                GitHubRelease release = releaseClient.fetchLatest();
                if (release.version().equals(currentVersion)) {
                    return ActionResult.success(new UpdateApplyResult(false, currentVersion));
                }

                Optional<ReleaseAsset> jarAsset = release.jarAsset();
                if (jarAsset.isEmpty()) {
                    return ActionResult.<UpdateApplyResult>failure(ActionResult.FailureReason.INTERNAL_ERROR,
                            "Release " + release.version() + " has no .jar asset to download.");
                }

                byte[] jarBytes = download(jarAsset.get().downloadUrl());
                Optional<ReleaseAsset> sha256Asset = release.sha256Asset();
                if (sha256Asset.isPresent()) {
                    String expected = parseSha256(download(sha256Asset.get().downloadUrl()));
                    String actual = sha256Hex(jarBytes);
                    if (!expected.equalsIgnoreCase(actual)) {
                        return ActionResult.<UpdateApplyResult>failure(ActionResult.FailureReason.INTERNAL_ERROR,
                                "Downloaded jar for " + release.version() + " failed its SHA-256 check - not applied.");
                    }
                }

                stage(jarBytes);
                return ActionResult.success(new UpdateApplyResult(true, release.version()));
            } catch (NoSuchAlgorithmException e) {
                // SHA-256 is a mandatory JDK algorithm (java.security.MessageDigest spec) - unreachable in practice.
                return ActionResult.<UpdateApplyResult>failure(ActionResult.FailureReason.INTERNAL_ERROR, "SHA-256 unavailable: " + e.getMessage());
            } catch (IOException | InterruptedException e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                return ActionResult.<UpdateApplyResult>failure(
                        ActionResult.FailureReason.INTERNAL_ERROR, "Could not download the update: " + e.getMessage());
            }
        });
    }

    private byte[] download(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(DOWNLOAD_TIMEOUT).GET().build();
        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Download of " + url + " returned HTTP " + response.statusCode());
        }
        return response.body();
    }

    /** {@code sha256sum}-format output ({@code "<hex>  <filename>"}, see {@code release.yml}) - only the hex digest matters here. */
    private String parseSha256(byte[] fileContents) {
        String text = new String(fileContents, java.nio.charset.StandardCharsets.UTF_8).strip();
        int firstSpace = text.indexOf(' ');
        return (firstSpace > 0 ? text.substring(0, firstSpace) : text).trim();
    }

    private String sha256Hex(byte[] data) throws NoSuchAlgorithmException {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(data);
        return HexFormat.of().formatHex(digest).toLowerCase(Locale.ROOT);
    }

    private void stage(byte[] jarBytes) throws IOException {
        Path updateFolder = Bukkit.getUpdateFolderFile().toPath();
        Files.createDirectories(updateFolder);
        Path target = updateFolder.resolve(currentJarFile.get().getFileName());
        Path temp = Files.createTempFile(updateFolder, "update-", ".jar.tmp");
        try {
            Files.write(temp, jarBytes);
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}

package dev.universaladmin.update;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Downloads a release's plugin jar into Bukkit's own {@code plugins/update/}
 * folder under the exact filename of the jar currently running, so Bukkit's
 * plugin loader swaps it in at the next server start. Deliberately not a
 * live hot-swap: replacing a running plugin's classes is a far more fragile
 * problem, and Bukkit already has a safe "apply on next restart" mechanism -
 * see {@code Bukkit#getUpdateFolderFile()}.
 *
 * <p>The jar is the asset named {@code assetPrefix + version + ".jar"} -
 * never just the first {@code .jar}, since a UniversalAdmin release also
 * carries the API, its sources and javadoc and the extensions. A
 * {@code <jar>.sha256} asset next to it (see {@code release.yml}) is checked
 * before anything is written; a mismatch stages nothing.
 */
public final class UpdateDownloader implements UpdateStager {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration DOWNLOAD_TIMEOUT = Duration.ofMinutes(2);

    private final Supplier<Path> updateFolder;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /** @param updateFolder where the server picks up updates - {@code Bukkit.getUpdateFolderFile().toPath()} */
    public UpdateDownloader(Supplier<Path> updateFolder) {
        this.updateFolder = updateFolder;
    }

    @Override
    public void stage(UpdateTarget target, GitHubRelease release) throws IOException, InterruptedException {
        ReleaseAsset jar = release.jarAsset(target.assetPrefix())
                .orElseThrow(() -> new IOException("Release " + release.version() + " of " + target.repository()
                        + " has no " + target.assetPrefix() + release.version() + ".jar"));
        byte[] jarBytes = download(jar.downloadUrl());
        Optional<ReleaseAsset> checksum = release.sha256Of(jar);
        if (checksum.isPresent()) {
            String expected = parseSha256(download(checksum.get().downloadUrl()));
            if (!expected.equalsIgnoreCase(sha256Hex(jarBytes))) {
                throw new IOException("The download of " + jar.name() + " failed its SHA-256 check - not staged.");
            }
        }
        write(target.jarFile().getFileName(), jarBytes);
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
    static String parseSha256(byte[] fileContents) {
        String text = new String(fileContents, StandardCharsets.UTF_8).strip();
        int firstSpace = text.indexOf(' ');
        return (firstSpace > 0 ? text.substring(0, firstSpace) : text).trim();
    }

    private static String sha256Hex(byte[] data) throws IOException {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(data);
            return HexFormat.of().formatHex(digest).toLowerCase(Locale.ROOT);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is a mandatory JDK algorithm (java.security.MessageDigest spec) - unreachable in practice.
            throw new IOException("SHA-256 unavailable: " + e.getMessage(), e);
        }
    }

    private void write(Path fileName, byte[] jarBytes) throws IOException {
        Path folder = updateFolder.get();
        Files.createDirectories(folder);
        Path target = folder.resolve(fileName);
        Path temp = Files.createTempFile(folder, "update-", ".jar.tmp");
        try {
            Files.write(temp, jarBytes);
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}

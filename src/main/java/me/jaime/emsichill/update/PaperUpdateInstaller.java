package me.jaime.emsichill.update;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.concurrent.CompletableFuture;
import java.util.jar.JarFile;

import me.jaime.emsichill.Main;

/** Downloads a Paper server jar into a safe staging folder for the next restart. */
final class PaperUpdateInstaller {
    private static final int BUFFER_SIZE = 16 * 1024;

    private final Main plugin;

    PaperUpdateInstaller(final Main plugin) {
        this.plugin = plugin;
    }

    CompletableFuture<PaperUpdateInstallResult> prepare(final PaperBuildInfo build) {
        if (!this.plugin.settings().getBoolean("updates.paper.download.enabled", true)) {
            return CompletableFuture.completedFuture(PaperUpdateInstallResult.of(
                PaperUpdateInstallResult.Status.DISABLED, build.identifier(), "Paper downloads are disabled"));
        }
        try {
            URI uri = URI.create(build.download().url());
            if (!uri.getScheme().equalsIgnoreCase("https")
                || !uri.getHost().equalsIgnoreCase("fill-data.papermc.io")) {
                return CompletableFuture.completedFuture(PaperUpdateInstallResult.of(
                    PaperUpdateInstallResult.Status.FAILED, build.identifier(), "download host is not allowed"));
            }
        } catch (IllegalArgumentException exception) {
            return CompletableFuture.completedFuture(PaperUpdateInstallResult.of(
                PaperUpdateInstallResult.Status.FAILED, build.identifier(), "invalid download URL"));
        }

        return CompletableFuture.supplyAsync(() -> this.download(build));
    }

    private PaperUpdateInstallResult download(final PaperBuildInfo build) {
        Path temporary = null;
        try {
            Path directory = this.downloadDirectory();
            Files.createDirectories(directory);
            temporary = directory.resolve(".paper-download.part");
            Duration timeout = this.timeout();
            HttpClient client = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
            HttpRequest request = HttpRequest.newBuilder(URI.create(build.download().url()))
                .timeout(timeout)
                .header("Accept", "application/octet-stream")
                .header("User-Agent", this.userAgent())
                .GET()
                .build();
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                closeQuietly(response.body());
                return PaperUpdateInstallResult.of(PaperUpdateInstallResult.Status.FAILED,
                    build.identifier(), "PaperMC responded with HTTP " + response.statusCode());
            }

            DownloadDigest downloaded = copyAndDigest(response.body(), temporary, this.maximumDownloadBytes());
            if (downloaded.size() != build.download().size()) throw new IOException("download size does not match");
            if (!downloaded.sha256().equalsIgnoreCase(build.download().sha256())) {
                throw new IOException("download SHA-256 does not match");
            }
            validateServerJar(temporary);

            Path destination = directory.resolve(build.download().name());
            if (!this.keepOldDownloads()) this.removeOlderPaperJars(directory, destination);
            moveReplacing(temporary, destination);
            return PaperUpdateInstallResult.prepared(build, destination);
        } catch (IOException | InterruptedException | RuntimeException exception) {
            if (temporary != null) deleteQuietly(temporary);
            if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
            return PaperUpdateInstallResult.of(PaperUpdateInstallResult.Status.FAILED,
                build.identifier(), rootMessage(exception));
        }
    }

    private Path downloadDirectory() {
        String configured = this.plugin.settings().getString("updates.paper.download.directory", "server-updates");
        Path target = Paths.get(configured == null ? "server-updates" : configured.trim());
        if (!target.isAbsolute()) target = this.serverRoot().resolve(target);
        return target.toAbsolutePath().normalize();
    }

    private Path serverRoot() {
        Path dataFolder = this.plugin.getDataFolder().toPath().toAbsolutePath().normalize();
        Path pluginsFolder = dataFolder.getParent();
        return pluginsFolder == null || pluginsFolder.getParent() == null
            ? Paths.get(".").toAbsolutePath().normalize()
            : pluginsFolder.getParent().toAbsolutePath().normalize();
    }

    private Duration timeout() {
        return Duration.ofSeconds(Math.max(15, Math.min(600,
            this.plugin.settings().getInt("updates.paper.download.timeout-seconds", 180))));
    }

    private long maximumDownloadBytes() {
        long megabytes = Math.max(10L, Math.min(500L,
            this.plugin.settings().getLong("updates.paper.download.max-download-megabytes", 120L)));
        return megabytes * 1024L * 1024L;
    }

    private boolean keepOldDownloads() {
        return this.plugin.settings().getBoolean("updates.paper.download.keep-old-downloads", false);
    }

    private String userAgent() {
        return this.plugin.settings().getString("updates.paper.user-agent",
                "EmsiChill/${version} (https://github.com/Jme-05/EmsiChill)")
            .replace("${version}", this.plugin.getPluginMeta().getVersion());
    }

    private void removeOlderPaperJars(final Path directory, final Path destination) throws IOException {
        try (DirectoryStream<Path> files = Files.newDirectoryStream(directory, "paper-*.jar")) {
            for (Path file : files) {
                if (!file.equals(destination) && Files.isRegularFile(file)) Files.delete(file);
            }
        }
    }

    static void validateServerJar(final Path file) throws IOException {
        try (JarFile jar = new JarFile(file.toFile(), true)) {
            if (jar.getManifest() == null) throw new IOException("the file is not a valid server jar");
        }
    }

    private static DownloadDigest copyAndDigest(
        final InputStream input,
        final Path destination,
        final long maximumBytes
    ) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
        long total = 0L;
        try (input; OutputStream output = Files.newOutputStream(destination,
            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                total += read;
                if (total > maximumBytes) throw new IOException("download exceeds the configured maximum size");
                output.write(buffer, 0, read);
                digest.update(buffer, 0, read);
            }
        }
        return new DownloadDigest(total, HexFormat.of().formatHex(digest.digest()));
    }

    private static void moveReplacing(final Path source, final Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void closeQuietly(final InputStream stream) {
        try {
            stream.close();
        } catch (IOException ignored) {
        }
    }

    private static void deleteQuietly(final Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
        }
    }

    private static String rootMessage(final Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private record DownloadDigest(long size, String sha256) {
    }
}

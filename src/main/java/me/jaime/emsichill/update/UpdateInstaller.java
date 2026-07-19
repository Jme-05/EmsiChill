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
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.concurrent.CompletableFuture;
import java.util.jar.JarFile;

import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.InvalidDescriptionException;

import me.jaime.emsichill.Main;

/** Descarga y valida un JAR antes de entregarlo al actualizador de Paper. */
final class UpdateInstaller {
    private static final String MAIN_CLASS = "me.jaime.emsichill.Main";
    private static final int BUFFER_SIZE = 16 * 1024;

    private final Main plugin;

    UpdateInstaller(final Main plugin) {
        this.plugin = plugin;
    }

    CompletableFuture<UpdateInstallResult> prepare(final ReleaseInfo release) {
        if (!this.plugin.settings().getBoolean("updates.install.enabled", true)) {
            return CompletableFuture.completedFuture(UpdateInstallResult.of(
                UpdateInstallResult.Status.DISABLED, release.tag(), "instalación desactivada"));
        }
        ReleaseAsset asset = release.asset();
        if (asset == null) {
            return CompletableFuture.completedFuture(UpdateInstallResult.of(
                UpdateInstallResult.Status.FAILED, release.tag(), "la Release no contiene el JAR esperado"));
        }

        long maximumBytes = this.maximumDownloadBytes();
        if (asset.hasVerifiedMetadata() && asset.size() > maximumBytes) {
            return CompletableFuture.completedFuture(UpdateInstallResult.of(
                UpdateInstallResult.Status.FAILED, release.tag(), "metadatos del JAR inválidos"));
        }
        if (!asset.hasVerifiedMetadata()
            && !this.plugin.settings().getBoolean("updates.install.allow-feed-fallback", true)) {
            return CompletableFuture.completedFuture(UpdateInstallResult.of(
                UpdateInstallResult.Status.FAILED, release.tag(), "la instalación mediante el feed está desactivada"));
        }
        URI uri = URI.create(asset.downloadUrl());
        if (!uri.getScheme().equalsIgnoreCase("https") || !uri.getHost().equalsIgnoreCase("github.com")) {
            return CompletableFuture.completedFuture(UpdateInstallResult.of(
                UpdateInstallResult.Status.FAILED, release.tag(), "origen de descarga no permitido"));
        }

        Duration timeout = Duration.ofSeconds(Math.max(10, Math.min(300,
            this.plugin.settings().getInt("updates.install.timeout-seconds", 60))));
        HttpClient client = HttpClient.newBuilder()
            .connectTimeout(timeout)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
        HttpRequest request = HttpRequest.newBuilder(uri)
            .timeout(timeout)
            .header("Accept", "application/octet-stream")
            .header("User-Agent", "EmsiChill/" + this.plugin.getPluginMeta().getVersion())
            .GET()
            .build();
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream())
            .thenApply(response -> this.stageResponse(response, release, maximumBytes))
            .exceptionally(failure -> UpdateInstallResult.of(
                UpdateInstallResult.Status.FAILED, release.tag(), rootMessage(failure)));
    }

    private UpdateInstallResult stageResponse(
        final HttpResponse<InputStream> response,
        final ReleaseInfo release,
        final long maximumBytes
    ) {
        if (response.statusCode() != 200) {
            closeQuietly(response.body());
            return UpdateInstallResult.of(UpdateInstallResult.Status.FAILED, release.tag(),
                "GitHub respondió con HTTP " + response.statusCode());
        }

        Path temporary = null;
        try {
            Path updateDirectory = this.updateDirectory();
            Files.createDirectories(updateDirectory);
            temporary = updateDirectory.resolve(".emsichill-download.part");
            DownloadDigest downloaded = copyAndDigest(response.body(), temporary, maximumBytes);
            ReleaseAsset asset = release.asset();
            if (asset.hasVerifiedMetadata()) {
                if (downloaded.size() != asset.size()) throw new IOException("el tamaño descargado no coincide");
                String expectedHash = asset.digest().substring("sha256:".length());
                if (!downloaded.sha256().equalsIgnoreCase(expectedHash)) {
                    throw new IOException("el SHA-256 descargado no coincide");
                }
            }
            validateJar(temporary, release.tag());

            Path destination = updateDirectory.resolve(asset.name());
            this.removeOlderStagedJars(updateDirectory, destination);
            moveReplacing(temporary, destination);
            return UpdateInstallResult.prepared(release.tag(), destination);
        } catch (IOException | RuntimeException exception) {
            if (temporary != null) deleteQuietly(temporary);
            return UpdateInstallResult.of(UpdateInstallResult.Status.FAILED, release.tag(), exception.getMessage());
        }
    }

    private Path updateDirectory() throws IOException {
        if (this.plugin.getServer().getUpdateFolder().isBlank()) {
            throw new IOException("la carpeta de actualización de Paper está desactivada");
        }
        return this.plugin.getServer().getUpdateFolderFile().toPath().toAbsolutePath().normalize();
    }

    private long maximumDownloadBytes() {
        long megabytes = Math.max(1L, Math.min(100L,
            this.plugin.settings().getLong("updates.install.max-download-megabytes", 25L)));
        return megabytes * 1024L * 1024L;
    }

    private void removeOlderStagedJars(final Path directory, final Path destination) throws IOException {
        try (DirectoryStream<Path> files = Files.newDirectoryStream(directory, "EmsiChill-*.jar")) {
            for (Path file : files) {
                if (!file.equals(destination) && Files.isRegularFile(file)) Files.delete(file);
            }
        }
    }

    static void validateJar(final Path file, final String expectedVersion) throws IOException {
        try (JarFile jar = new JarFile(file.toFile(), true)) {
            var descriptorEntry = jar.getJarEntry("plugin.yml");
            if (descriptorEntry == null || jar.getJarEntry("me/jaime/emsichill/Main.class") == null) {
                throw new IOException("el archivo no es un JAR válido de EmsiChill");
            }
            try (var reader = new java.io.InputStreamReader(
                jar.getInputStream(descriptorEntry), java.nio.charset.StandardCharsets.UTF_8)) {
                PluginDescriptionFile descriptor;
                try {
                    descriptor = new PluginDescriptionFile(reader);
                } catch (InvalidDescriptionException exception) {
                    throw new IOException("plugin.yml no es válido", exception);
                }
                if (!descriptor.getName().equals("EmsiChill") || !descriptor.getMain().equals(MAIN_CLASS)
                    || !sameVersion(descriptor.getVersion(), expectedVersion)) {
                    throw new IOException("la identidad o versión del JAR no coincide");
                }
            }
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
            throw new IllegalStateException("SHA-256 no está disponible", exception);
        }
        long total = 0L;
        try (input; OutputStream output = Files.newOutputStream(destination,
            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                total += read;
                if (total > maximumBytes) throw new IOException("la descarga supera el tamaño permitido");
                output.write(buffer, 0, read);
                digest.update(buffer, 0, read);
            }
        }
        return new DownloadDigest(total, HexFormat.of().formatHex(digest.digest()));
    }

    private static boolean sameVersion(final String first, final String second) {
        var left = VersionNumber.parse(first);
        var right = VersionNumber.parse(second);
        return left.isPresent() && right.isPresent() && left.get().compareTo(right.get()) == 0;
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

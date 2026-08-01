package me.jaime.emsichill.resourcepack;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Uploads generated packs to a public HTTPS host when the Minecraft host cannot expose an HTTP port. */
final class RemotePackUploader {
    private final HttpClient client;
    private final URI apiBase;
    private final Duration timeout;
    private final Path cacheFile;
    private final long maximumUploadBytes;
    private final String userAgent;
    private final Map<String, CacheEntry> cache;

    RemotePackUploader(final URI apiBase, final Duration timeout, final Path cacheFile,
                       final long maximumUploadBytes, final String userAgent) {
        this.apiBase = URI.create(ensureTrailingSlash(apiBase.toString()));
        this.timeout = timeout;
        this.cacheFile = cacheFile;
        this.maximumUploadBytes = maximumUploadBytes;
        this.userAgent = userAgent;
        this.client = HttpClient.newBuilder()
            .connectTimeout(timeout)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
        this.cache = this.loadCache();
    }

    synchronized Map<String, String> host(final List<LocalResourcePackBuilder.BuildResult> packs)
        throws IOException {
        Map<String, String> urls = new LinkedHashMap<>();
        boolean cacheChanged = false;
        for (LocalResourcePackBuilder.BuildResult pack : packs) {
            CacheEntry cached = this.cache.get(pack.sha1Hex());
            CacheEntry hosted = cached == null ? null : this.validateCached(pack, cached);
            if (hosted == null) {
                hosted = this.upload(pack);
                this.cache.put(pack.sha1Hex(), hosted);
                cacheChanged = true;
            } else if (!hosted.equals(cached)) {
                this.cache.put(pack.sha1Hex(), hosted);
                cacheChanged = true;
            }
            urls.put(pack.sha1Hex(), hosted.downloadUrl());
        }
        if (cacheChanged) this.saveCache();
        return Map.copyOf(urls);
    }

    private CacheEntry validateCached(final LocalResourcePackBuilder.BuildResult pack, final CacheEntry cached)
        throws IOException {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(this.apiBase.resolve("packs/" + cached.uuid()))
            .timeout(this.timeout)
            .header("Accept", "application/json")
            .header("User-Agent", this.userAgent)
            .GET()
            .build();
        try {
            HttpResponse<String> response = this.client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404) return null;
            if (response.statusCode() != 200) return cached;
            CacheEntry validated = parseResponse(response.body(), pack.sha1Hex());
            return validated == null ? null : validated;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("remote resource-pack validation was interrupted", exception);
        } catch (IOException exception) {
            return cached;
        }
    }

    private CacheEntry upload(final LocalResourcePackBuilder.BuildResult pack) throws IOException {
        if (pack.size() > this.maximumUploadBytes) {
            throw new IOException(pack.sourceName() + " exceeds the remote upload size limit");
        }
        String boundary = "EmsiChill-" + UUID.randomUUID();
        byte[] start = ("--" + boundary + "\r\n"
            + "Content-Disposition: form-data; name=\"file\"; filename=\"resource-pack.zip\"\r\n"
            + "Content-Type: application/zip\r\n\r\n").getBytes(StandardCharsets.US_ASCII);
        byte[] end = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.US_ASCII);
        HttpRequest.BodyPublisher body = HttpRequest.BodyPublishers.concat(
            HttpRequest.BodyPublishers.ofByteArray(start),
            HttpRequest.BodyPublishers.ofFile(pack.file()),
            HttpRequest.BodyPublishers.ofByteArray(end));
        HttpRequest request = HttpRequest.newBuilder()
            .uri(this.apiBase.resolve("packs"))
            .timeout(this.timeout)
            .header("Accept", "application/json")
            .header("Content-Type", "multipart/form-data; boundary=" + boundary)
            .header("User-Agent", this.userAgent)
            .POST(body)
            .build();
        try {
            HttpResponse<String> response = this.client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200 && response.statusCode() != 201) {
                throw new IOException("remote pack host returned HTTP " + response.statusCode());
            }
            CacheEntry uploaded = parseResponse(response.body(), pack.sha1Hex());
            if (uploaded == null) throw new IOException("remote pack host returned an invalid response");
            return uploaded;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("remote resource-pack upload was interrupted", exception);
        }
    }

    private static CacheEntry parseResponse(final String body, final String expectedSha1) {
        try {
            JsonElement decoded = JsonParser.parseString(body);
            if (!decoded.isJsonObject()) return null;
            JsonObject root = decoded.getAsJsonObject();
            if (!root.has("success") || !root.get("success").getAsBoolean()) return null;
            JsonElement dataElement = root.get("data");
            if (dataElement == null || !dataElement.isJsonObject()) return null;
            JsonObject data = dataElement.getAsJsonObject();
            String uuid = string(data.get("uuid"));
            String sha1 = string(data.get("sha1"));
            String downloadUrl = string(data.get("download_url"));
            if (uuid == null || sha1 == null || downloadUrl == null
                || !sha1.equalsIgnoreCase(expectedSha1)) return null;
            UUID.fromString(uuid);
            URI uri = URI.create(downloadUrl);
            if (uri.getHost() == null || (!uri.getScheme().equalsIgnoreCase("https")
                && !uri.getScheme().equalsIgnoreCase("http"))) return null;
            return new CacheEntry(uuid, downloadUrl);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private Map<String, CacheEntry> loadCache() {
        Map<String, CacheEntry> loaded = new LinkedHashMap<>();
        if (!Files.isRegularFile(this.cacheFile)) return loaded;
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(this.cacheFile)) {
            properties.load(input);
        } catch (IOException exception) {
            return loaded;
        }
        for (String key : properties.stringPropertyNames()) {
            if (!key.endsWith(".uuid")) continue;
            String hash = key.substring(0, key.length() - ".uuid".length());
            String uuid = properties.getProperty(key);
            String url = properties.getProperty(hash + ".url");
            if (ResourcePackManager.validSha1(hash) && validEntry(uuid, url)) {
                loaded.put(hash, new CacheEntry(uuid, url));
            }
        }
        return loaded;
    }

    private void saveCache() throws IOException {
        Files.createDirectories(this.cacheFile.getParent());
        Properties properties = new Properties();
        for (Map.Entry<String, CacheEntry> entry : this.cache.entrySet()) {
            properties.setProperty(entry.getKey() + ".uuid", entry.getValue().uuid());
            properties.setProperty(entry.getKey() + ".url", entry.getValue().downloadUrl());
        }
        Path temporary = Files.createTempFile(this.cacheFile.getParent(), "remote-hosting-", ".tmp");
        try {
            try (OutputStream output = Files.newOutputStream(temporary)) {
                properties.store(output, "EmsiChill remote resource-pack cache");
            }
            try {
                Files.move(temporary, this.cacheFile, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, this.cacheFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static String string(final JsonElement value) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) return null;
        String string = value.getAsString();
        return string.isBlank() ? null : string;
    }

    private static String ensureTrailingSlash(final String value) {
        return value.endsWith("/") ? value : value + "/";
    }

    private static boolean validEntry(final String uuid, final String url) {
        if (uuid == null || url == null) return false;
        try {
            UUID.fromString(uuid);
            URI uri = URI.create(url);
            return uri.getHost() != null && ("https".equalsIgnoreCase(uri.getScheme())
                || "http".equalsIgnoreCase(uri.getScheme()));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private record CacheEntry(String uuid, String downloadUrl) {
    }
}

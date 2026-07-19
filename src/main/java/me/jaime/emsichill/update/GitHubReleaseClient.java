package me.jaime.emsichill.update;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Consulta únicamente la última Release pública del repositorio configurado. */
final class GitHubReleaseClient {
    private static final String API_VERSION = "2026-03-10";

    private final HttpClient client;
    private final Duration timeout;
    private final String repository;
    private final String userAgent;

    GitHubReleaseClient(final String repository, final String userAgent, final Duration timeout) {
        this.repository = repository;
        this.userAgent = userAgent;
        this.timeout = timeout;
        this.client = HttpClient.newBuilder()
            .connectTimeout(timeout)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    }

    CompletableFuture<ReleaseInfo> latestRelease() {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("https://api.github.com/repos/" + this.repository + "/releases/latest"))
            .timeout(this.timeout)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", API_VERSION)
            .header("User-Agent", this.userAgent)
            .GET()
            .build();
        return this.client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenApply(response -> {
                if (response.statusCode() != 200) {
                    throw new IllegalStateException("GitHub respondió con HTTP " + response.statusCode());
                }
                String tag = readStringField(response.body(), "tag_name");
                String page = readStringField(response.body(), "html_url");
                if (tag == null || page == null) throw new IllegalStateException("Respuesta de GitHub incompleta");
                String notes = readStringField(response.body(), "body");
                return new ReleaseInfo(tag, page, notes == null ? "" : notes,
                    readReleaseAsset(response.body(), tag));
            });
    }

    static ReleaseAsset readReleaseAsset(final String json, final String tag) {
        String assets = readArrayField(json, "assets");
        if (assets == null) return null;
        String version = tag.startsWith("v") || tag.startsWith("V") ? tag.substring(1) : tag;
        String expectedName = "EmsiChill-" + version + ".jar";
        for (String object : readObjects(assets)) {
            String name = readStringField(object, "name");
            if (name == null || !name.equalsIgnoreCase(expectedName)) continue;
            String downloadUrl = readStringField(object, "browser_download_url");
            String digest = readStringField(object, "digest");
            Long size = readLongField(object, "size");
            if (downloadUrl == null || digest == null || size == null) return null;
            return new ReleaseAsset(name, downloadUrl, size, digest);
        }
        return null;
    }

    // Lee cadenas JSON respetando escapes; solo se necesitan dos campos de la respuesta pública.
    static String readStringField(final String json, final String field) {
        String key = "\"" + field + "\"";
        int position = json.indexOf(key);
        if (position < 0) return null;
        position = json.indexOf(':', position + key.length());
        if (position < 0) return null;
        while (++position < json.length() && Character.isWhitespace(json.charAt(position))) { }
        if (position >= json.length() || json.charAt(position) != '"') return null;

        StringBuilder value = new StringBuilder();
        for (position++; position < json.length(); position++) {
            char current = json.charAt(position);
            if (current == '"') return value.toString();
            if (current != '\\') {
                value.append(current);
                continue;
            }
            if (++position >= json.length()) return null;
            char escaped = json.charAt(position);
            switch (escaped) {
                case '"', '\\', '/' -> value.append(escaped);
                case 'b' -> value.append('\b');
                case 'f' -> value.append('\f');
                case 'n' -> value.append('\n');
                case 'r' -> value.append('\r');
                case 't' -> value.append('\t');
                case 'u' -> {
                    if (position + 4 >= json.length()) return null;
                    try {
                        value.append((char) Integer.parseInt(json.substring(position + 1, position + 5), 16));
                    } catch (NumberFormatException exception) {
                        return null;
                    }
                    position += 4;
                }
                default -> { return null; }
            }
        }
        return null;
    }

    static Long readLongField(final String json, final String field) {
        int position = findValueStart(json, field);
        if (position < 0) return null;
        int end = position;
        while (end < json.length() && Character.isDigit(json.charAt(end))) end++;
        if (end == position) return null;
        try {
            return Long.parseLong(json.substring(position, end));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static String readArrayField(final String json, final String field) {
        int position = findValueStart(json, field);
        if (position < 0 || json.charAt(position) != '[') return null;
        int end = matchingEnd(json, position, '[', ']');
        return end < 0 ? null : json.substring(position + 1, end);
    }

    private static List<String> readObjects(final String array) {
        List<String> objects = new ArrayList<>();
        int position = 0;
        while ((position = array.indexOf('{', position)) >= 0) {
            int end = matchingEnd(array, position, '{', '}');
            if (end < 0) break;
            objects.add(array.substring(position, end + 1));
            position = end + 1;
        }
        return objects;
    }

    private static int findValueStart(final String json, final String field) {
        String key = "\"" + field + "\"";
        int position = json.indexOf(key);
        if (position < 0) return -1;
        position = json.indexOf(':', position + key.length());
        if (position < 0) return -1;
        do {
            position++;
        } while (position < json.length() && Character.isWhitespace(json.charAt(position)));
        return position < json.length() ? position : -1;
    }

    private static int matchingEnd(final String text, final int start, final char opening, final char closing) {
        int depth = 0;
        boolean quoted = false;
        boolean escaped = false;
        for (int position = start; position < text.length(); position++) {
            char current = text.charAt(position);
            if (quoted) {
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == '"') {
                    quoted = false;
                }
                continue;
            }
            if (current == '"') {
                quoted = true;
            } else if (current == opening) {
                depth++;
            } else if (current == closing && --depth == 0) {
                return position;
            }
        }
        return -1;
    }
}

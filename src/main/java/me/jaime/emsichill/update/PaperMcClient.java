package me.jaime.emsichill.update;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Small REST client for PaperMC's downloads service. */
final class PaperMcClient {
    private static final String BASE_URL = "https://fill.papermc.io/v3/projects/";
    private static final Pattern VERSION_PATTERN =
        Pattern.compile("\"([0-9]+(?:\\.[0-9]+)*(?:-[A-Za-z0-9._-]+)?)\"");

    private final HttpClient client;
    private final String project;
    private final String userAgent;
    private final Duration timeout;
    private final boolean includeExperimentalBuilds;
    private final int maxVersionSearch;

    PaperMcClient(
        final String project,
        final String userAgent,
        final Duration timeout,
        final boolean includeExperimentalBuilds,
        final int maxVersionSearch
    ) {
        this.project = project;
        this.userAgent = userAgent;
        this.timeout = timeout;
        this.includeExperimentalBuilds = includeExperimentalBuilds;
        this.maxVersionSearch = maxVersionSearch;
        this.client = HttpClient.newBuilder()
            .connectTimeout(timeout)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    }

    CompletableFuture<PaperBuildInfo> latestBuild() {
        HttpRequest request = this.request(URI.create(BASE_URL + this.project));
        return this.client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenCompose(response -> {
                if (response.statusCode() != 200) {
                    throw new IllegalStateException("PaperMC responded with HTTP " + response.statusCode());
                }
                List<String> versions = readVersions(response.body(), this.includeExperimentalBuilds);
                if (versions.isEmpty()) throw new IllegalStateException("PaperMC returned no versions");
                return this.findBuild(versions.subList(0, Math.min(versions.size(), this.maxVersionSearch)), 0);
            });
    }

    private CompletableFuture<PaperBuildInfo> findBuild(final List<String> versions, final int index) {
        if (index >= versions.size()) {
            return CompletableFuture.failedFuture(new IllegalStateException("No acceptable Paper build was found"));
        }
        return this.buildForVersion(versions.get(index))
            .handle((build, failure) -> failure == null
                ? CompletableFuture.completedFuture(build)
                : this.findBuild(versions, index + 1))
            .thenCompose(future -> future);
    }

    private CompletableFuture<PaperBuildInfo> buildForVersion(final String minecraftVersion) {
        HttpRequest request = this.request(URI.create(BASE_URL + this.project
            + "/versions/" + minecraftVersion + "/builds"));
        return this.client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenApply(response -> {
                if (response.statusCode() != 200) {
                    throw new IllegalStateException("PaperMC responded with HTTP " + response.statusCode());
                }
                PaperBuildInfo build = readBuild(this.project, minecraftVersion, response.body(),
                    this.includeExperimentalBuilds);
                if (build == null) throw new IllegalStateException("No stable Paper build for " + minecraftVersion);
                return build;
            });
    }

    private HttpRequest request(final URI uri) {
        return HttpRequest.newBuilder(uri)
            .timeout(this.timeout)
            .header("Accept", "application/json")
            .header("User-Agent", this.userAgent)
            .GET()
            .build();
    }

    static List<String> readVersions(final String json, final boolean includePrereleases) {
        String versionsObject = GitHubReleaseClient.readObjectField(json, "versions");
        if (versionsObject == null) return List.of();
        Set<String> versions = new LinkedHashSet<>();
        int position = 0;
        while ((position = versionsObject.indexOf('[', position)) >= 0) {
            int end = matchingEnd(versionsObject, position, '[', ']');
            if (end < 0) break;
            Matcher matcher = VERSION_PATTERN.matcher(versionsObject.substring(position + 1, end));
            while (matcher.find()) {
                String version = matcher.group(1);
                if (!includePrereleases && version.contains("-")) continue;
                versions.add(version);
            }
            position = end + 1;
        }
        return new ArrayList<>(versions);
    }

    static PaperBuildInfo readBuild(
        final String project,
        final String minecraftVersion,
        final String json,
        final boolean includeExperimentalBuilds
    ) {
        for (String object : GitHubReleaseClient.readObjects(json)) {
            String channel = GitHubReleaseClient.readStringField(object, "channel");
            if (channel == null) continue;
            if (!includeExperimentalBuilds && !channel.equalsIgnoreCase("STABLE")) continue;
            Long build = GitHubReleaseClient.readLongField(object, "id");
            PaperBuildDownload download = readDownload(object);
            if (build == null || build > Integer.MAX_VALUE || download == null || !download.hasVerifiedMetadata()) {
                continue;
            }
            return new PaperBuildInfo(project, minecraftVersion, build.intValue(), channel, download);
        }
        return null;
    }

    private static PaperBuildDownload readDownload(final String buildObject) {
        String downloads = GitHubReleaseClient.readObjectField(buildObject, "downloads");
        if (downloads == null) return null;
        String server = GitHubReleaseClient.readObjectField(downloads, "server:default");
        if (server == null) return null;
        String checksums = GitHubReleaseClient.readObjectField(server, "checksums");
        if (checksums == null) return null;
        String name = GitHubReleaseClient.readStringField(server, "name");
        String url = GitHubReleaseClient.readStringField(server, "url");
        Long size = GitHubReleaseClient.readLongField(server, "size");
        String sha256 = GitHubReleaseClient.readStringField(checksums, "sha256");
        return size == null ? null : new PaperBuildDownload(name, url, size, sha256);
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

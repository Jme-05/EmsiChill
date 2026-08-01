package me.jaime.emsichill.resourcepack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipFile;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ResourcePackManagerTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void buildsLocalPackFromDirectory() throws Exception {
        Path source = this.temporaryDirectory.resolve("Server Pack");
        Files.createDirectories(source.resolve("assets/example"));
        Files.writeString(source.resolve("pack.mcmeta"), "{\"pack\":{\"pack_format\":1,\"description\":\"Test\"}}",
            StandardCharsets.UTF_8);
        Files.writeString(source.resolve("assets/example/value.txt"), "local", StandardCharsets.UTF_8);

        List<LocalResourcePackBuilder.BuildResult> results = LocalResourcePackBuilder.build(
            this.temporaryDirectory, 16L * 1024L * 1024L);

        assertEquals(1, results.size());
        LocalResourcePackBuilder.BuildResult result = results.getFirst();
        assertNotNull(result);
        assertEquals("Server Pack", result.sourceName());
        assertTrue(ResourcePackManager.validSha1(result.sha1Hex()));
        assertTrue(Files.isRegularFile(result.file()));
        try (ZipFile zip = new ZipFile(result.file().toFile())) {
            assertNotNull(zip.getEntry("pack.mcmeta"));
            assertNotNull(zip.getEntry("assets/example/value.txt"));
        }
    }

    @Test
    void buildsPacksIndependentlyInAlphabeticalOrder() throws Exception {
        this.createPack("01-base", "base");
        this.createPack("02-custom", "custom");

        List<LocalResourcePackBuilder.BuildResult> first = LocalResourcePackBuilder.build(
            this.temporaryDirectory, 16L * 1024L * 1024L);
        List<LocalResourcePackBuilder.BuildResult> second = LocalResourcePackBuilder.build(
            this.temporaryDirectory, 16L * 1024L * 1024L);

        assertEquals(List.of("01-base", "02-custom"), first.stream().map(
            LocalResourcePackBuilder.BuildResult::sourceName).toList());
        assertEquals(first.stream().map(LocalResourcePackBuilder.BuildResult::sha1Hex).toList(),
            second.stream().map(LocalResourcePackBuilder.BuildResult::sha1Hex).toList());
        assertFalse(first.get(0).sha1Hex().equals(first.get(1).sha1Hex()));
    }

    @Test
    void servesOnlyActiveGeneratedPacks() throws Exception {
        this.createPack("server", "served");
        LocalResourcePackBuilder.BuildResult pack = LocalResourcePackBuilder.build(
            this.temporaryDirectory, 16L * 1024L * 1024L).getFirst();

        try (LocalPackHttpServer server = new LocalPackHttpServer(); HttpClient client = HttpClient.newHttpClient()) {
            server.start("127.0.0.1", 0);
            server.setActivePacks(List.of(pack));
            URI uri = URI.create("http://127.0.0.1:" + server.port()
                + LocalPackHttpServer.pathFor(pack.sha1Hex()));
            HttpResponse<byte[]> response = client.send(HttpRequest.newBuilder(uri).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());

            assertEquals(200, response.statusCode());
            assertEquals(Files.readAllBytes(pack.file()).length, response.body().length);
            assertEquals("application/zip", response.headers().firstValue("Content-Type").orElseThrow());
        }
    }

    @Test
    void buildsStableUuidAndAutomaticUrls() {
        UUID first = ResourcePackManager.packId("local-server-pack");
        UUID second = ResourcePackManager.packId("local-server-pack");

        assertEquals(first, second);
        assertEquals("http://play.example.com:8165",
            ResourcePackManager.automaticBaseUrl("play.example.com", 8165));
        assertEquals("http://[2001:db8::1]:8165",
            ResourcePackManager.automaticBaseUrl("2001:db8::1", 8165));
    }

    @Test
    void validatesSha1Format() {
        assertTrue(ResourcePackManager.validSha1("A".repeat(40)));
        assertFalse(ResourcePackManager.validSha1("a".repeat(39)));
        assertFalse(ResourcePackManager.validSha1("g".repeat(40)));
        assertFalse(ResourcePackManager.validSha1("0".repeat(40)));
    }

    private void createPack(final String name, final String value) throws Exception {
        Path source = this.temporaryDirectory.resolve(name);
        Files.createDirectories(source.resolve("assets/example"));
        Files.writeString(source.resolve("pack.mcmeta"),
            "{\"pack\":{\"pack_format\":1,\"description\":\"" + name + "\"}}",
            StandardCharsets.UTF_8);
        Files.writeString(source.resolve("assets/example/value.txt"), value, StandardCharsets.UTF_8);
    }
}

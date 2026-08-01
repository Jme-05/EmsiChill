package me.jaime.emsichill.resourcepack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RemotePackUploaderTest {
    private static final String REMOTE_ID = "550e8400-e29b-41d4-a716-446655440000";

    @TempDir
    Path temporaryDirectory;

    @Test
    void uploadsThenValidatesTheCachedRemotePack() throws Exception {
        LocalResourcePackBuilder.BuildResult pack = this.createPack();
        AtomicInteger uploads = new AtomicInteger();
        AtomicInteger validations = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/packs", exchange -> {
            if (exchange.getRequestMethod().equals("POST")) uploads.incrementAndGet();
            else validations.incrementAndGet();
            exchange.getRequestBody().readAllBytes();
            this.respond(exchange, pack.sha1Hex(), server.getAddress().getPort());
        });
        server.start();
        try {
            URI api = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/api/v1/");
            RemotePackUploader uploader = new RemotePackUploader(api, Duration.ofSeconds(5),
                this.temporaryDirectory.resolve("cache/remote.properties"), 16L * 1024L * 1024L, "Test");

            Map<String, String> first = uploader.host(List.of(pack));
            RemotePackUploader restarted = new RemotePackUploader(api, Duration.ofSeconds(5),
                this.temporaryDirectory.resolve("cache/remote.properties"), 16L * 1024L * 1024L, "Test");
            Map<String, String> second = restarted.host(List.of(pack));

            assertEquals(first, second);
            assertTrue(first.get(pack.sha1Hex()).endsWith("/download"));
            assertEquals(1, uploads.get());
            assertEquals(1, validations.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void rejectsPacksAboveTheRemoteLimitBeforeUploading() throws Exception {
        LocalResourcePackBuilder.BuildResult pack = this.createPack();
        RemotePackUploader uploader = new RemotePackUploader(URI.create("https://example.com/api/"),
            Duration.ofSeconds(5), this.temporaryDirectory.resolve("cache.properties"), 1L, "Test");

        assertThrows(IOException.class, () -> uploader.host(List.of(pack)));
    }

    private LocalResourcePackBuilder.BuildResult createPack() throws Exception {
        Path source = this.temporaryDirectory.resolve("pack");
        Files.createDirectories(source.resolve("assets/example"));
        Files.writeString(source.resolve("pack.mcmeta"),
            "{\"pack\":{\"pack_format\":1,\"description\":\"Remote test\"}}", StandardCharsets.UTF_8);
        Files.writeString(source.resolve("assets/example/value.txt"), "test", StandardCharsets.UTF_8);
        return LocalResourcePackBuilder.build(this.temporaryDirectory, 16L * 1024L * 1024L).getFirst();
    }

    private void respond(final HttpExchange exchange, final String sha1, final int port) throws java.io.IOException {
        String json = "{\"success\":true,\"data\":{"
            + "\"uuid\":\"" + REMOTE_ID + "\","
            + "\"sha1\":\"" + sha1 + "\","
            + "\"download_url\":\"http:\\/\\/127.0.0.1:" + port + "\\/download\"}}";
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(exchange.getRequestMethod().equals("POST") ? 201 : 200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }
}

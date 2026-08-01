package me.jaime.emsichill.resourcepack;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/** Serves only the currently active generated resource packs. */
final class LocalPackHttpServer implements AutoCloseable {
    static final String PATH_PREFIX = "/emsichill-packs/";

    private HttpServer server;
    private ExecutorService executor;
    private volatile Map<String, LocalResourcePackBuilder.BuildResult> activePacks = Map.of();

    void start(final String bindAddress, final int port) throws IOException {
        this.close();
        this.server = HttpServer.create(new InetSocketAddress(bindAddress, port), 0);
        this.server.createContext(PATH_PREFIX, this::handle);
        this.executor = Executors.newFixedThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "EmsiChill-Pack-HTTP");
            thread.setDaemon(true);
            return thread;
        });
        this.server.setExecutor(this.executor);
        this.server.start();
    }

    void setActivePacks(final List<LocalResourcePackBuilder.BuildResult> packs) {
        Map<String, LocalResourcePackBuilder.BuildResult> byPath = new LinkedHashMap<>();
        for (LocalResourcePackBuilder.BuildResult pack : packs) {
            byPath.putIfAbsent(pathFor(pack.sha1Hex()), pack);
        }
        this.activePacks = Map.copyOf(byPath);
    }

    boolean running() {
        return this.server != null;
    }

    int port() {
        return this.server == null ? -1 : this.server.getAddress().getPort();
    }

    private void handle(final HttpExchange exchange) throws IOException {
        try (exchange) {
            String method = exchange.getRequestMethod().toUpperCase(Locale.ROOT);
            if (!method.equals("GET") && !method.equals("HEAD")) {
                exchange.getResponseHeaders().set("Allow", "GET, HEAD");
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            LocalResourcePackBuilder.BuildResult pack = this.activePacks.get(exchange.getRequestURI().getPath());
            if (pack == null || !Files.isRegularFile(pack.file())) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }

            String etag = "\"" + pack.sha1Hex() + "\"";
            exchange.getResponseHeaders().set("Content-Type", "application/zip");
            exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=server-pack.zip");
            exchange.getResponseHeaders().set("Cache-Control", "public, max-age=31536000, immutable");
            exchange.getResponseHeaders().set("ETag", etag);
            exchange.getResponseHeaders().set("Content-Length", Long.toString(pack.size()));
            if (etag.equals(exchange.getRequestHeaders().getFirst("If-None-Match"))) {
                exchange.sendResponseHeaders(304, -1);
                return;
            }
            if (method.equals("HEAD")) {
                exchange.sendResponseHeaders(200, -1);
                return;
            }
            exchange.sendResponseHeaders(200, pack.size());
            try (OutputStream output = exchange.getResponseBody()) {
                Files.copy(pack.file(), output);
            }
        }
    }

    static String pathFor(final String sha1) {
        return PATH_PREFIX + sha1.toLowerCase(Locale.ROOT) + ".zip";
    }

    @Override
    public void close() {
        if (this.server != null) {
            this.server.stop(0);
            this.server = null;
        }
        if (this.executor != null) {
            this.executor.shutdownNow();
            this.executor = null;
        }
    }
}

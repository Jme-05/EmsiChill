package me.jaime.emsichill.resourcepack;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import com.destroystokyo.paper.event.player.PlayerConnectionCloseEvent;
import io.papermc.paper.connection.PlayerConfigurationConnection;
import io.papermc.paper.connection.PlayerConnection;
import io.papermc.paper.event.connection.configuration.PlayerConnectionInitialConfigureEvent;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.resource.ResourcePackStatus;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

import me.jaime.emsichill.Main;
import me.jaime.emsichill.config.ConfigFile;

/** Builds, hosts and sends locally managed server resource packs. */
public final class ResourcePackManager implements Listener {
    private static final int CONFIG_VERSION = 5;
    private static final String DEFAULT_REMOTE_API = "https://mcpacks.dev/api/v1/";

    private final Main plugin;
    private final LocalPackHttpServer httpServer = new LocalPackHttpServer();
    private final AtomicBoolean reloadInProgress = new AtomicBoolean();
    private final Set<UUID> sentDuringConfiguration = ConcurrentHashMap.newKeySet();
    private final Set<UUID> obsoletePackIds = ConcurrentHashMap.newKeySet();
    private ConfigFile configFile;
    private volatile List<LocalResourcePackBuilder.BuildResult> activePacks = List.of();
    private volatile Map<String, String> remotePackUrls = Map.of();
    private RemotePackUploader remoteUploader;
    private long sendDelayTicks;
    private long maximumUncompressedBytes;
    private boolean clearExisting;
    private boolean logStatus;
    private boolean required;
    private boolean sendDuringConfiguration;
    private boolean hostingEnabled;
    private HostingMode hostingMode;
    private String prompt;
    private String bindAddress;
    private String publicBaseUrl;
    private int httpPort;
    private boolean running;
    private String hostingError;

    public ResourcePackManager(final Main plugin) {
        this.plugin = plugin;
        this.reloadConfiguration();
    }

    public void reloadConfiguration() {
        boolean restartHosting = this.running;
        if (restartHosting) this.httpServer.close();
        if (this.configFile == null) this.configFile = new ConfigFile(this.plugin, "ResourcePacks/config.yml");
        else this.configFile.reload();
        this.migrateConfiguration();
        this.loadSettings();
        if (restartHosting) this.startHttpServer();
    }

    private void migrateConfiguration() {
        int version = this.configFile.yaml().getInt("_meta.config-version", 0);
        if (version >= CONFIG_VERSION) return;
        if (version < 2) {
            this.configFile.yaml().set("send-delay-ticks", 20L);
            this.configFile.yaml().set("clear-existing", false);
            this.configFile.yaml().set("log-status", false);
            this.configFile.yaml().set("pack.required", true);
            this.configFile.yaml().set("pack.prompt", "");
            this.configFile.yaml().set("pack.maximum-uncompressed-megabytes", 512);
            this.configFile.yaml().set("hosting.enabled", true);
            this.configFile.yaml().set("hosting.bind-address", "0.0.0.0");
            this.configFile.yaml().set("hosting.port", 8165);
            this.configFile.yaml().set("hosting.public-base-url", "auto");
        }
        if (!this.configFile.yaml().isSet("send-during-configuration")) {
            this.configFile.yaml().set("send-during-configuration", true);
        }
        if (!this.configFile.yaml().isSet("hosting.mode")
            || (version < 5 && this.configFile.yaml().getString("hosting.mode", "local").equalsIgnoreCase("local"))) {
            this.configFile.yaml().set("hosting.mode", "auto");
        }
        if (!this.configFile.yaml().isSet("hosting.remote.api-base-url")) {
            this.configFile.yaml().set("hosting.remote.api-base-url", DEFAULT_REMOTE_API);
            this.configFile.yaml().set("hosting.remote.timeout-seconds", 180);
            this.configFile.yaml().set("hosting.remote.maximum-upload-megabytes", 250);
        }
        this.configFile.yaml().set("_meta.config-version", CONFIG_VERSION);
        this.configFile.save();
    }

    private void loadSettings() {
        this.sendDelayTicks = Math.max(0L, this.configFile.yaml().getLong("send-delay-ticks", 20L));
        this.sendDuringConfiguration = this.configFile.yaml().getBoolean("send-during-configuration", true);
        this.clearExisting = this.configFile.yaml().getBoolean("clear-existing", false);
        this.logStatus = this.configFile.yaml().getBoolean("log-status", false);
        this.required = this.configFile.yaml().getBoolean("pack.required", true);
        this.prompt = this.blankToNull(this.configFile.yaml().getString("pack.prompt", ""));
        long megabytes = Math.max(1L,
            Math.min(2048L, this.configFile.yaml().getLong("pack.maximum-uncompressed-megabytes", 512L)));
        this.maximumUncompressedBytes = megabytes * 1024L * 1024L;
        this.hostingEnabled = this.configFile.yaml().getBoolean("hosting.enabled", true);
        this.hostingMode = HostingMode.parse(this.configFile.yaml().getString("hosting.mode", "auto"));
        this.bindAddress = this.configFile.yaml().getString("hosting.bind-address", "0.0.0.0");
        int configuredPort = this.configFile.yaml().getInt("hosting.port", 8165);
        this.httpPort = configuredPort > 0 && configuredPort <= 65535 ? configuredPort : 8165;
        String configuredPublicUrl = this.configFile.yaml().getString("hosting.public-base-url", "auto");
        this.publicBaseUrl = validPublicBaseUrl(configuredPublicUrl) ? stripTrailingSlash(configuredPublicUrl) : "auto";
        String configuredRemoteApi = this.configFile.yaml().getString("hosting.remote.api-base-url",
            DEFAULT_REMOTE_API);
        URI remoteApi = validPublicBaseUrl(configuredRemoteApi) ? URI.create(configuredRemoteApi)
            : URI.create(DEFAULT_REMOTE_API);
        int remoteTimeout = Math.max(15,
            Math.min(600, this.configFile.yaml().getInt("hosting.remote.timeout-seconds", 180)));
        long remoteMegabytes = Math.max(1L,
            Math.min(250L, this.configFile.yaml().getLong("hosting.remote.maximum-upload-megabytes", 250L)));
        Path cache = this.plugin.getDataFolder().toPath().resolve("ResourcePacks/.generated/remote-hosting.properties");
        this.remoteUploader = new RemotePackUploader(remoteApi, Duration.ofSeconds(remoteTimeout), cache,
            remoteMegabytes * 1024L * 1024L, "EmsiChill/" + this.plugin.getPluginMeta().getVersion());
    }

    public void start() {
        if (this.running) return;
        this.running = true;
        this.startHttpServer();
        this.reloadLocalPacks().whenComplete((result, failure) -> {
            if (failure != null || result == null || result.status() == ReloadStatus.FAILED) {
                String detail = failure == null ? (result == null ? "empty result" : result.error())
                    : failure.getMessage();
                this.plugin.getLogger().warning("Could not prepare local resource packs: " + detail);
            } else if (result.status() == ReloadStatus.EMPTY) {
                this.plugin.getLogger().info("No local resource packs were found in ResourcePacks/.");
            } else if (result.status() == ReloadStatus.LOADED) {
                String effectiveMode = this.remotePackUrls.isEmpty() ? "local" : "remote";
                this.plugin.getLogger().info(result.sources() + " local resource pack(s) ready using "
                    + effectiveMode + " hosting.");
            }
        });
    }

    public void stop() {
        this.running = false;
        this.httpServer.close();
        this.activePacks = List.of();
        this.remotePackUrls = Map.of();
        this.sentDuringConfiguration.clear();
        this.obsoletePackIds.clear();
        this.reloadInProgress.set(false);
    }

    private void startHttpServer() {
        this.hostingError = null;
        if (!this.hostingEnabled || !this.running || this.hostingMode == HostingMode.REMOTE) return;
        try {
            this.httpServer.start(this.bindAddress, this.httpPort);
            this.httpServer.setActivePacks(this.activePacks);
            this.plugin.getLogger().info("Local resource-pack server listening on " + this.bindAddress
                + ":" + this.httpPort + ".");
        } catch (IOException | IllegalArgumentException exception) {
            this.hostingError = exception.getMessage();
            if (this.hostingMode == HostingMode.AUTO) {
                this.plugin.getLogger().info("Local resource-pack hosting is unavailable; "
                    + "auto mode will use remote HTTPS hosting.");
            } else {
                this.plugin.getLogger().warning("Could not start the local resource-pack server: "
                    + exception.getMessage());
            }
        }
    }

    public CompletableFuture<ReloadResult> reloadLocalPacks() {
        return this.reloadLocalPacks(false);
    }

    public CompletableFuture<ReloadResult> reloadConfiguredPacks() {
        return this.reloadLocalPacks(true);
    }

    public CompletableFuture<ReloadResult> reloadConfiguredPacks(final Consumer<ReloadPhase> progress) {
        return this.reloadLocalPacks(true, progress);
    }

    private CompletableFuture<ReloadResult> reloadLocalPacks(final boolean reloadConfig) {
        return this.reloadLocalPacks(reloadConfig, ignored -> { });
    }

    private CompletableFuture<ReloadResult> reloadLocalPacks(
        final boolean reloadConfig,
        final Consumer<ReloadPhase> progress
    ) {
        if (!this.enabled()) {
            return CompletableFuture.completedFuture(new ReloadResult(
                ReloadStatus.DISABLED, 0, 0, null, 0L, "resource-pack module is disabled"));
        }
        if (!this.reloadInProgress.compareAndSet(false, true)) {
            return CompletableFuture.completedFuture(new ReloadResult(
                ReloadStatus.IN_PROGRESS, 0, 0, null, 0L, null));
        }
        notifyProgress(progress, ReloadPhase.PREPARING);
        if (reloadConfig) this.reloadConfiguration();
        if (!this.running) {
            this.running = true;
            this.startHttpServer();
        }
        CompletableFuture<ReloadResult> future = new CompletableFuture<>();
        Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> {
            List<LocalResourcePackBuilder.BuildResult> built = List.of();
            Map<String, String> hostedUrls = Map.of();
            IOException failure = null;
            try {
                notifyProgress(progress, ReloadPhase.BUILDING);
                built = this.buildLocalPacks();
                notifyProgress(progress, ReloadPhase.HOSTING);
                hostedUrls = this.prepareHosting(built);
            } catch (IOException exception) {
                failure = exception;
            }
            List<LocalResourcePackBuilder.BuildResult> result = built;
            Map<String, String> resultUrls = hostedUrls;
            IOException error = failure;
            Bukkit.getScheduler().runTask(this.plugin, () -> {
                try {
                    if (error != null) {
                        future.complete(new ReloadResult(ReloadStatus.FAILED, 0, 0, null, 0L,
                            error.getMessage()));
                        return;
                    }
                    notifyProgress(progress, ReloadPhase.ACTIVATING);
                    List<LocalResourcePackBuilder.BuildResult> previous = this.activePacks;
                    this.activate(result, resultUrls);
                    this.rememberObsoletePacks(previous, result);
                    if (result.isEmpty()) {
                        future.complete(new ReloadResult(ReloadStatus.EMPTY, 0, 0, null, 0L, null));
                        return;
                    }
                    future.complete(new ReloadResult(ReloadStatus.LOADED, result.size(), 0,
                        hashes(result), totalBytes(result), null));
                } finally {
                    this.reloadInProgress.set(false);
                }
            });
        });
        return future;
    }

    private static void notifyProgress(final Consumer<ReloadPhase> progress, final ReloadPhase phase) {
        if (progress == null) return;
        try {
            progress.accept(phase);
        } catch (RuntimeException ignored) {
            // A visual progress listener must never interrupt pack preparation.
        }
    }

    public PushResult pushOnline() {
        if (!this.enabled()) return new PushResult(PushStatus.DISABLED, 0, 0, "resource-pack module is disabled");
        List<LocalResourcePackBuilder.BuildResult> packs = this.activePacks;
        if (packs.isEmpty()) return new PushResult(PushStatus.EMPTY, 0, 0, null);
        if (!this.hostingReady(packs)) this.startHttpServer();
        if (!this.hostingReady(packs)) {
            return new PushResult(PushStatus.FAILED, packs.size(), 0, this.hostingFailure());
        }
        int sent = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            for (UUID obsoleteId : this.obsoletePackIds) player.removeResourcePack(obsoleteId);
            if (this.sendPacks(player)) sent++;
        }
        this.obsoletePackIds.clear();
        return new PushResult(PushStatus.SENT, packs.size(), sent, null);
    }

    private List<LocalResourcePackBuilder.BuildResult> buildLocalPacks() throws IOException {
        Path directory = this.plugin.getDataFolder().toPath().resolve("ResourcePacks");
        return LocalResourcePackBuilder.build(directory, this.maximumUncompressedBytes);
    }

    private Map<String, String> prepareHosting(final List<LocalResourcePackBuilder.BuildResult> packs)
        throws IOException {
        if (packs.isEmpty()) return Map.of();
        if (!this.hostingEnabled) throw new IOException("resource-pack hosting is disabled");
        if (this.hostingMode == HostingMode.LOCAL) {
            if (!this.httpServer.running()) throw new IOException(this.hostingFailure());
            return Map.of();
        }
        if (this.hostingMode == HostingMode.AUTO && this.httpServer.running()) return Map.of();
        return this.remoteUploader.host(packs);
    }

    private void activate(final List<LocalResourcePackBuilder.BuildResult> packs,
                          final Map<String, String> hostedUrls) {
        this.activePacks = List.copyOf(packs);
        this.remotePackUrls = Map.copyOf(hostedUrls);
        this.httpServer.setActivePacks(this.activePacks);
    }

    @EventHandler
    public void onInitialConfigure(final PlayerConnectionInitialConfigureEvent event) {
        if (!this.sendDuringConfiguration || !this.enabled() || this.activePacks.isEmpty()) return;
        PlayerConfigurationConnection connection = event.getConnection();
        UUID playerId = connection.getProfile().getId();
        if (playerId == null || !(connection instanceof PlayerConnection playerConnection)) return;
        String playerName = connection.getProfile().getName();
        if (this.sendPacks(connection.getAudience(), playerConnection.getVirtualHost(), playerName, true)) {
            this.sentDuringConfiguration.add(playerId);
        }
    }

    @EventHandler
    public void onConnectionClose(final PlayerConnectionCloseEvent event) {
        this.sentDuringConfiguration.remove(event.getPlayerUniqueId());
    }

    @EventHandler
    public void onJoin(final PlayerJoinEvent event) {
        if (this.sentDuringConfiguration.remove(event.getPlayer().getUniqueId())) return;
        if (!this.enabled() || this.activePacks.isEmpty()) return;
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
            if (player.isOnline()) this.sendPacks(player);
        }, this.sendDelayTicks);
    }

    @EventHandler
    public void onResourcePackStatus(final PlayerResourcePackStatusEvent event) {
        if (!this.enabled()) return;
        LocalResourcePackBuilder.BuildResult pack = this.pack(event.getID());
        if (pack == null) return;
        String status = event.getStatus().name().toLowerCase(Locale.ROOT);
        switch (event.getStatus()) {
            case DECLINED, FAILED_DOWNLOAD, INVALID_URL, FAILED_RELOAD, DISCARDED -> this.plugin.getLogger()
                .warning("Resource pack " + pack.sourceName() + " for " + event.getPlayer().getName()
                    + ": " + status);
            default -> {
                if (this.logStatus) this.plugin.getLogger()
                    .info("Resource pack " + pack.sourceName() + " for " + event.getPlayer().getName()
                        + ": " + status);
            }
        }
    }

    private boolean sendPacks(final Player player) {
        return this.sendPacks(player, player.getVirtualHost(), player.getName(), false);
    }

    private boolean sendPacks(final Audience audience, final InetSocketAddress virtualHost,
                              final String playerName, final boolean includeCallback) {
        List<LocalResourcePackBuilder.BuildResult> packs = this.activePacks;
        if (!this.enabled() || packs.isEmpty() || !this.hostingReady(packs)) return false;
        try {
            List<ResourcePackInfo> packInfo = packs.stream()
                .map(pack -> ResourcePackInfo.resourcePackInfo(packId("local:" + pack.sourceName()),
                    URI.create(this.packUrl(virtualHost, pack.sha1Hex())), pack.sha1Hex()))
                .toList();
            ResourcePackRequest.Builder request = ResourcePackRequest.resourcePackRequest()
                .packs(packInfo)
                .replace(this.clearExisting)
                .required(this.required);
            if (this.prompt != null) request.prompt(Component.text(this.prompt));
            if (includeCallback) request.callback((id, status, target) ->
                this.logConfigurationStatus(id, status, playerName));
            audience.sendResourcePacks(request.build());
            return true;
        } catch (IllegalArgumentException exception) {
            this.plugin.getLogger().warning("Could not send local resource packs to " + playerName + ": "
                + exception.getMessage());
            return false;
        }
    }

    private LocalResourcePackBuilder.BuildResult pack(final UUID id) {
        for (LocalResourcePackBuilder.BuildResult pack : this.activePacks) {
            if (packId("local:" + pack.sourceName()).equals(id)) return pack;
        }
        return null;
    }

    private void rememberObsoletePacks(final List<LocalResourcePackBuilder.BuildResult> previous,
                                       final List<LocalResourcePackBuilder.BuildResult> current) {
        List<UUID> currentIds = current.stream()
            .map(pack -> packId("local:" + pack.sourceName()))
            .toList();
        for (LocalResourcePackBuilder.BuildResult pack : previous) {
            UUID id = packId("local:" + pack.sourceName());
            if (currentIds.contains(id)) continue;
            this.obsoletePackIds.add(id);
        }
    }

    private void logConfigurationStatus(final UUID id, final ResourcePackStatus packStatus,
                                        final String playerName) {
        LocalResourcePackBuilder.BuildResult pack = this.pack(id);
        if (pack == null) return;
        String status = packStatus.name().toLowerCase(Locale.ROOT);
        switch (packStatus) {
            case DECLINED, FAILED_DOWNLOAD, INVALID_URL, FAILED_RELOAD, DISCARDED -> this.plugin.getLogger()
                .warning("Resource pack " + pack.sourceName() + " for " + playerName + ": " + status);
            default -> {
                if (this.logStatus) this.plugin.getLogger()
                    .info("Resource pack " + pack.sourceName() + " for " + playerName + ": " + status);
            }
        }
    }

    private String packUrl(final InetSocketAddress virtualHost, final String sha1) {
        String remoteUrl = this.remotePackUrls.get(sha1);
        if (remoteUrl != null) return remoteUrl;
        String base = this.publicBaseUrl;
        if (base.equalsIgnoreCase("auto")) {
            String host = virtualHost == null ? "127.0.0.1" : virtualHost.getHostString();
            base = automaticBaseUrl(host, this.httpPort);
        }
        return stripTrailingSlash(base) + LocalPackHttpServer.pathFor(sha1);
    }

    static String automaticBaseUrl(final String virtualHost, final int port) {
        String host = virtualHost == null || virtualHost.isBlank() ? "127.0.0.1" : virtualHost.trim();
        if (host.contains(":") && !host.startsWith("[")) host = "[" + host + "]";
        return "http://" + host + ":" + port;
    }

    static UUID packId(final String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            return UUID.nameUUIDFromBytes(("EmsiChill:" + value).getBytes(StandardCharsets.UTF_8));
        }
    }

    private static String hashes(final List<LocalResourcePackBuilder.BuildResult> packs) {
        return packs.stream().map(LocalResourcePackBuilder.BuildResult::sha1Hex).reduce((left, right) ->
            left + "," + right).orElse("");
    }

    private static long totalBytes(final List<LocalResourcePackBuilder.BuildResult> packs) {
        return packs.stream().mapToLong(LocalResourcePackBuilder.BuildResult::size).sum();
    }

    static boolean validSha1(final String value) {
        if (value == null || !value.matches("(?i)[0-9a-f]{40}") || value.matches("0{40}")) return false;
        try {
            return HexFormat.of().parseHex(value).length == 20;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static boolean validPublicBaseUrl(final String value) {
        if (value == null || value.isBlank() || value.equalsIgnoreCase("auto")) return true;
        try {
            URI uri = URI.create(value);
            return uri.getHost() != null && (uri.getScheme().equalsIgnoreCase("http")
                || uri.getScheme().equalsIgnoreCase("https"));
        } catch (IllegalArgumentException | NullPointerException exception) {
            return false;
        }
    }

    private String blankToNull(final String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String stripTrailingSlash(final String value) {
        String stripped = value;
        while (stripped.endsWith("/")) stripped = stripped.substring(0, stripped.length() - 1);
        return stripped;
    }

    private String hostingFailure() {
        if (!this.hostingEnabled) return "resource-pack hosting is disabled";
        if (this.hostingMode == HostingMode.REMOTE) return "remote resource-pack hosting is unavailable";
        return this.hostingError == null ? "local HTTP hosting is unavailable" : this.hostingError;
    }

    private boolean hostingReady(final List<LocalResourcePackBuilder.BuildResult> packs) {
        if (this.httpServer.running()) return true;
        return !packs.isEmpty() && packs.stream().allMatch(pack -> this.remotePackUrls.containsKey(pack.sha1Hex()));
    }

    private boolean enabled() {
        return this.plugin.moduleEnabled("resource-packs");
    }

    public enum ReloadStatus {
        LOADED,
        EMPTY,
        IN_PROGRESS,
        DISABLED,
        FAILED
    }

    public enum ReloadPhase {
        PREPARING,
        BUILDING,
        HOSTING,
        ACTIVATING
    }

    public enum PushStatus {
        SENT,
        EMPTY,
        DISABLED,
        FAILED
    }

    private enum HostingMode {
        LOCAL,
        REMOTE,
        AUTO;

        private static HostingMode parse(final String value) {
            if (value == null) return AUTO;
            try {
                return valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                return AUTO;
            }
        }
    }

    public record ReloadResult(
        ReloadStatus status,
        int sources,
        int players,
        String sha1,
        long bytes,
        String error
    ) {
    }

    public record PushResult(PushStatus status, int packs, int players, String error) {
    }
}

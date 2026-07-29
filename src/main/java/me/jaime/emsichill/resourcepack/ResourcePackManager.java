package me.jaime.emsichill.resourcepack;

import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

import me.jaime.emsichill.Main;
import me.jaime.emsichill.config.ConfigFile;

/** Envía resource packs configurados cuando un jugador entra al servidor. */
public final class ResourcePackManager implements Listener {
    private final Main plugin;
    private ConfigFile configFile;
    private File playerStateFile;
    private YamlConfiguration playerState;
    private List<PackDefinition> packs = List.of();
    private long sendDelayTicks;
    private boolean clearExisting;
    private boolean logStatus;
    private boolean sendOnlyNewOrChanged;

    public ResourcePackManager(final Main plugin) {
        this.plugin = plugin;
        this.reloadConfiguration();
    }

    public void reloadConfiguration() {
        if (this.configFile == null) this.configFile = new ConfigFile(this.plugin, "ResourcePacks/config.yml");
        else this.configFile.reload();
        this.sendDelayTicks = Math.max(0L, this.configFile.yaml().getLong("send-delay-ticks", 40L));
        this.clearExisting = this.configFile.yaml().getBoolean("clear-existing", false);
        this.logStatus = this.configFile.yaml().getBoolean("log-status", true);
        this.sendOnlyNewOrChanged = this.configFile.yaml().getBoolean("send-only-new-or-changed", false);
        this.reloadPlayerState();
        this.packs = this.loadPacks();
    }

    @EventHandler
    public void onJoin(final PlayerJoinEvent event) {
        if (!this.enabled() || this.packs.isEmpty()) return;
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
            if (player.isOnline()) this.sendPacks(player);
        }, this.sendDelayTicks);
    }

    @EventHandler
    public void onResourcePackStatus(final PlayerResourcePackStatusEvent event) {
        if (!this.enabled()) return;
        PackDefinition pack = this.pack(event.getID());
        if (pack == null) return;
        if (event.getStatus() == PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED) {
            this.rememberLoadedPack(event.getPlayer(), pack);
        }
        if (!this.logStatus) return;
        switch (event.getStatus()) {
            case ACCEPTED, DOWNLOADED, SUCCESSFULLY_LOADED, DECLINED, FAILED_DOWNLOAD, INVALID_URL, FAILED_RELOAD,
                DISCARDED -> this.plugin.getLogger()
                .info("Resource pack " + pack.name() + " para " + event.getPlayer().getName() + ": "
                    + event.getStatus().name().toLowerCase(Locale.ROOT));
            default -> { }
        }
    }

    private void sendPacks(final Player player) {
        if (!this.enabled() || this.packs.isEmpty()) return;
        List<PackDefinition> sendable = this.packsToSend(player);
        if (sendable.isEmpty()) return;
        if (this.clearExisting && !this.sendOnlyNewOrChanged) player.removeResourcePacks();
        for (PackDefinition pack : sendable) {
            try {
                player.addResourcePack(pack.id(), pack.url(), pack.sha1(), pack.prompt(), pack.required());
            } catch (IllegalArgumentException exception) {
                this.plugin.getLogger().warning("Could not send resource pack " + pack.name()
                    + " to " + player.getName() + ": " + exception.getMessage());
            }
        }
    }

    private List<PackDefinition> packsToSend(final Player player) {
        if (!this.sendOnlyNewOrChanged) return this.packs;
        List<PackDefinition> sendable = new ArrayList<>();
        for (PackDefinition pack : this.packs) {
            if (!this.alreadyLoaded(player, pack)) sendable.add(pack);
        }
        return sendable;
    }

    private boolean enabled() {
        return this.plugin.moduleEnabled("resource-packs");
    }

    private PackDefinition pack(final UUID id) {
        for (PackDefinition pack : this.packs) {
            if (pack.id().equals(id)) return pack;
        }
        return null;
    }

    private List<PackDefinition> loadPacks() {
        List<PackDefinition> loaded = new ArrayList<>();
        Set<UUID> ids = new HashSet<>();
        for (Map<?, ?> entry : this.configFile.yaml().getMapList("packs")) {
            if (!entryEnabled(entry)) continue;
            PackDefinition pack = parse(entry);
            if (pack == null) {
                this.plugin.getLogger().warning("Resource pack ignorado: revisa id, url y sha1 en ResourcePacks/config.yml.");
                continue;
            }
            if (!ids.add(pack.id())) {
                this.plugin.getLogger().warning("Resource pack duplicado ignorado: " + pack.name());
                continue;
            }
            loaded.add(pack);
        }
        ConfigurationSection legacy = this.configFile.yaml().getConfigurationSection("pack");
        if (loaded.isEmpty() && legacy != null) {
            Map<String, Object> values = legacy.getValues(false);
            if (entryEnabled(values)) {
                PackDefinition pack = parse(values);
                if (pack != null) loaded.add(pack);
            }
        }
        return List.copyOf(loaded);
    }

    private void reloadPlayerState() {
        if (this.playerStateFile == null) {
            this.playerStateFile = new File(this.plugin.getDataFolder(), "ResourcePacks/players.yml");
        }
        this.playerState = this.plugin.dataStore().load(this.playerStateFile);
    }

    private boolean alreadyLoaded(final Player player, final PackDefinition pack) {
        String path = "players." + player.getUniqueId() + ".packs." + pack.id();
        return pack.sha1Hex().equalsIgnoreCase(this.playerState.getString(path + ".sha1", ""));
    }

    private void rememberLoadedPack(final Player player, final PackDefinition pack) {
        String path = "players." + player.getUniqueId() + ".packs." + pack.id();
        this.playerState.set(path + ".name", pack.name());
        this.playerState.set(path + ".sha1", pack.sha1Hex());
        this.playerState.set(path + ".loaded-at", Instant.now().getEpochSecond());
        this.plugin.dataStore().saveAsync(this.playerStateFile, this.playerState);
    }

    static PackDefinition parse(final Map<?, ?> entry) {
        String name = string(entry.get("name"), string(entry.get("id"), "server-pack"));
        String url = string(entry.get("url"), "");
        String sha1 = string(entry.get("sha1"), string(entry.get("hash"), ""));
        if (!validUrl(url) || !validSha1(sha1)) return null;
        String prompt = string(entry.get("prompt"), "");
        String sha1Hex = sha1.toLowerCase(Locale.ROOT);
        return new PackDefinition(
            packId(string(entry.get("id"), url)),
            name,
            url,
            sha1Hex,
            HexFormat.of().parseHex(sha1Hex),
            Boolean.parseBoolean(string(entry.get("required"), "false")),
            prompt.isBlank() ? null : prompt
        );
    }

    static UUID packId(final String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            return UUID.nameUUIDFromBytes(("EmsiChill:" + value).getBytes(StandardCharsets.UTF_8));
        }
    }

    static boolean validSha1(final String value) {
        return value != null && value.matches("(?i)[0-9a-f]{40}") && !value.matches("0{40}");
    }

    private static boolean validUrl(final String value) {
        if (value == null || value.isBlank()) return false;
        try {
            URI uri = URI.create(value);
            String scheme = uri.getScheme();
            return scheme != null && uri.getHost() != null
                && (scheme.equalsIgnoreCase("https") || scheme.equalsIgnoreCase("http"));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static boolean entryEnabled(final Map<?, ?> entry) {
        return Boolean.parseBoolean(string(entry.get("enabled"), "true"));
    }

    private static String string(final Object value, final String fallback) {
        return value == null ? fallback : value.toString().trim();
    }

    record PackDefinition(UUID id, String name, String url, String sha1Hex, byte[] sha1, boolean required, String prompt) {
    }
}

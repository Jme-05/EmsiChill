package me.jaime.emsichill.skin;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import me.jaime.emsichill.Main;

/** Guarda skins seleccionadas, favoritas e historial manteniendo compatibilidad con el YAML actual. */
public final class SkinRepository {
    public enum FavoriteResult {
        ADDED,
        ALREADY_EXISTS,
        LIMIT_REACHED
    }

    private final Main plugin;
    private final File playersFile;
    private final File cacheFile;
    private final Map<String, String> selectedSkins = new ConcurrentHashMap<>();
    private final Map<String, SkinTexture> textureCache = new ConcurrentHashMap<>();
    private final Map<String, List<String>> favorites = new ConcurrentHashMap<>();
    private final Map<String, List<SkinHistoryEntry>> histories = new ConcurrentHashMap<>();

    public SkinRepository(final Main plugin) {
        this.plugin = plugin;
        this.playersFile = new File(plugin.getDataFolder(), "Skin/players.yml");
        this.cacheFile = new File(plugin.getDataFolder(), "Skin/cache.yml");
        this.loadPlayers();
        this.loadCache();
    }

    public int selectedSkinCount() {
        return this.selectedSkins.size();
    }

    public int cachedTextureCount() {
        return this.textureCache.size();
    }

    public String selectedSkin(final String playerName) {
        return this.selectedSkins.get(key(playerName));
    }

    public void selectSkin(final String playerName, final String skinName) {
        this.selectedSkins.put(key(playerName), skinName);
    }

    public void clearSelection(final String playerName) {
        this.selectedSkins.remove(key(playerName));
    }

    public SkinTexture cachedTexture(final String skinName) {
        return this.textureCache.get(key(skinName));
    }

    public Set<String> cachedSkinNames() {
        return Set.copyOf(this.textureCache.keySet());
    }

    public void cacheTexture(final SkinTexture texture) {
        this.textureCache.put(key(texture.name()), texture);
    }

    public List<String> favorites(final String playerName) {
        return snapshot(this.favorites.get(key(playerName)));
    }

    public FavoriteResult addFavorite(final String playerName, final String skinName, final int limit,
                                      final boolean unlimited) {
        List<String> playerFavorites = this.favorites.computeIfAbsent(
            key(playerName), ignored -> Collections.synchronizedList(new ArrayList<>())
        );
        synchronized (playerFavorites) {
            if (playerFavorites.stream().anyMatch(value -> value.equalsIgnoreCase(skinName))) {
                return FavoriteResult.ALREADY_EXISTS;
            }
            if (!unlimited && playerFavorites.size() >= limit) {
                return FavoriteResult.LIMIT_REACHED;
            }
            playerFavorites.add(skinName);
            return FavoriteResult.ADDED;
        }
    }

    public boolean removeFavorite(final String playerName, final String skinName) {
        List<String> playerFavorites = this.favorites.get(key(playerName));
        return playerFavorites != null && playerFavorites.removeIf(value -> value.equalsIgnoreCase(skinName));
    }

    public List<SkinHistoryEntry> history(final String playerName) {
        return snapshot(this.histories.get(key(playerName)));
    }

    public void clearHistory(final String playerName) {
        this.histories.remove(key(playerName));
    }

    public void addHistory(final String playerName, final SkinHistoryEntry entry, final SkinSettings settings) {
        List<SkinHistoryEntry> history = this.histories.computeIfAbsent(
            key(playerName), ignored -> Collections.synchronizedList(new ArrayList<>())
        );
        // El proveedor puede terminar en otro hilo; la lista se modifica y recorta bajo el mismo bloqueo.
        synchronized (history) {
            if (settings.ignoreConsecutiveHistoryDuplicates() && !history.isEmpty()
                && history.getFirst().skin().equalsIgnoreCase(entry.skin())) {
                return;
            }
            history.addFirst(entry);
            while (history.size() > settings.historyLimit()) {
                history.removeLast();
            }
        }
    }

    public void save() {
        this.savePlayers();
        this.saveCache();
    }

    private void loadPlayers() {
        if (!this.playersFile.exists()) {
            return;
        }
        YamlConfiguration yaml = this.plugin.dataStore().load(this.playersFile);
        ConfigurationSection section = yaml.getConfigurationSection("players");
        if (section == null) {
            return;
        }

        for (String playerKey : section.getKeys(false)) {
            String path = "players." + playerKey;
            String skin = yaml.getString(path + ".skin");
            if (skin != null) {
                this.selectedSkins.put(key(playerKey), skin);
            }

            List<String> loadedFavorites = yaml.getStringList(path + ".favorites");
            if (!loadedFavorites.isEmpty()) {
                this.favorites.put(key(playerKey), synchronizedCopy(loadedFavorites));
            }

            List<SkinHistoryEntry> loadedHistory = Collections.synchronizedList(new ArrayList<>());
            for (Map<?, ?> value : yaml.getMapList(path + ".history")) {
                Object skinValue = value.get("skin");
                if (skinValue == null) {
                    continue;
                }
                long timestamp = value.get("timestamp") instanceof Number number ? number.longValue() : 0L;
                String actor = stringValue(value.get("actor"), playerKey);
                String source = stringValue(value.get("source"), "unknown");
                loadedHistory.add(new SkinHistoryEntry(skinValue.toString(), timestamp, actor, source));
            }
            if (!loadedHistory.isEmpty()) {
                this.histories.put(key(playerKey), loadedHistory);
            }
        }
    }

    private void savePlayers() {
        YamlConfiguration yaml = new YamlConfiguration();
        // La unión conserva jugadores que solo tengan selección, favoritos o historial.
        Set<String> playerKeys = new HashSet<>(this.selectedSkins.keySet());
        playerKeys.addAll(this.favorites.keySet());
        playerKeys.addAll(this.histories.keySet());

        for (String playerKey : playerKeys) {
            String path = "players." + playerKey;
            yaml.set(path + ".skin", this.selectedSkins.get(playerKey));
            yaml.set(path + ".favorites", snapshot(this.favorites.get(playerKey)));

            List<Map<String, Object>> historyValues = new ArrayList<>();
            for (SkinHistoryEntry entry : snapshot(this.histories.get(playerKey))) {
                Map<String, Object> value = new LinkedHashMap<>();
                value.put("skin", entry.skin());
                value.put("timestamp", entry.timestamp());
                value.put("actor", entry.actor());
                value.put("source", entry.source());
                historyValues.add(value);
            }
            yaml.set(path + ".history", historyValues);
        }
        this.plugin.dataStore().saveAsync(this.playersFile, yaml);
    }

    private void loadCache() {
        if (!this.cacheFile.exists()) {
            return;
        }
        YamlConfiguration yaml = this.plugin.dataStore().load(this.cacheFile);
        ConfigurationSection section = yaml.getConfigurationSection("skins");
        if (section == null) {
            return;
        }

        for (String skinKey : section.getKeys(false)) {
            String path = "skins." + skinKey;
            String name = yaml.getString(path + ".name");
            String value = yaml.getString(path + ".value");
            String signature = yaml.getString(path + ".signature");
            if (name != null && value != null && signature != null) {
                this.textureCache.put(key(skinKey), new SkinTexture(
                    name, value, signature, yaml.getLong(path + ".fetched-at")
                ));
            }
        }
    }

    private void saveCache() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<String, SkinTexture> entry : this.textureCache.entrySet()) {
            String path = "skins." + entry.getKey();
            SkinTexture texture = entry.getValue();
            yaml.set(path + ".name", texture.name());
            yaml.set(path + ".value", texture.value());
            yaml.set(path + ".signature", texture.signature());
            yaml.set(path + ".fetched-at", texture.fetchedAt());
        }
        this.plugin.dataStore().saveAsync(this.cacheFile, yaml);
    }

    private static String key(final String value) {
        return value.toLowerCase(Locale.ROOT);
    }

    private static String stringValue(final Object value, final String fallback) {
        return value == null ? fallback : value.toString();
    }

    private static <T> List<T> synchronizedCopy(final List<T> values) {
        return Collections.synchronizedList(new ArrayList<>(values));
    }

    private static <T> List<T> snapshot(final List<T> values) {
        if (values == null) {
            return List.of();
        }
        synchronized (values) {
            return List.copyOf(values);
        }
    }
}
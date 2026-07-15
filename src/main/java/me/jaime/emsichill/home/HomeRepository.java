package me.jaime.emsichill.home;

import java.io.File;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import me.jaime.emsichill.Main;

/**
 * Mantiene las homes por jugador, conserva sus nombres visibles y migra datos desde el antiguo
 * archivo de teletransportes.
 */
public final class HomeRepository {
    private final Main plugin;
    private final File dataFile;
    private final Map<String, Map<String, HomeLocation>> homes = new ConcurrentHashMap<>();
    private final Map<String, String> playerNames = new ConcurrentHashMap<>();

    public HomeRepository(final Main plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "Home/data.yml");
        this.loadData();
        this.migrateLegacyData();
    }

    public Map<String, HomeLocation> homes(final String playerName) {
        return this.homes.getOrDefault(key(playerName), Collections.emptyMap());
    }

    public Map<String, HomeLocation> editableHomes(final String playerName) {
        return this.homes.computeIfAbsent(key(playerName), ignored -> new ConcurrentHashMap<>());
    }

    public Collection<String> playerNames() {
        return this.playerNames.values();
    }

    public String displayName(final String playerName) {
        return this.playerNames.getOrDefault(key(playerName), playerName);
    }

    public void rememberPlayerName(final String playerName) {
        this.playerNames.put(key(playerName), playerName);
    }

    public void removePlayerIfEmpty(final String playerName) {
        this.homes.computeIfPresent(key(playerName), (ignored, values) -> values.isEmpty() ? null : values);
    }

    public int homeCount() {
        return this.homes.values().stream().mapToInt(Map::size).sum();
    }

    public int playerCount() {
        return this.homes.size();
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<String, Map<String, HomeLocation>> playerEntry : this.homes.entrySet()) {
            String playerKey = playerEntry.getKey();
            yaml.set("players." + playerKey + ".last-name", this.playerNames.getOrDefault(playerKey, playerKey));
            for (Map.Entry<String, HomeLocation> home : playerEntry.getValue().entrySet()) {
                home.getValue().save(yaml, "players." + playerKey + ".homes." + home.getKey());
            }
        }
        this.plugin.dataStore().saveAsync(this.dataFile, yaml);
    }

    private void loadData() {
        if (this.dataFile.exists()) {
            this.loadHomes(this.plugin.dataStore().load(this.dataFile));
        }
    }

    private void migrateLegacyData() {
        // Una carpeta Home ya poblada siempre tiene prioridad sobre los datos heredados.
        if (!this.homes.isEmpty()) {
            return;
        }
        File legacy = new File(this.plugin.getDataFolder(), "Teleport/data.yml");
        if (legacy.exists() && this.loadHomes(this.plugin.dataStore().load(legacy))) {
            this.save();
            this.plugin.getLogger().info("Homes antiguos migrados a Home/data.yml.");
        }
    }

    private boolean loadHomes(final YamlConfiguration yaml) {
        ConfigurationSection players = yaml.getConfigurationSection("players");
        if (players == null) {
            return false;
        }
        boolean loadedAny = false;
        for (String playerKey : players.getKeys(false)) {
            String normalized = key(playerKey);
            this.playerNames.put(normalized, yaml.getString("players." + playerKey + ".last-name", playerKey));
            ConfigurationSection section = yaml.getConfigurationSection("players." + playerKey + ".homes");
            if (section == null) {
                continue;
            }
            Map<String, HomeLocation> loaded = new ConcurrentHashMap<>();
            for (String homeName : section.getKeys(false)) {
                HomeLocation location = HomeLocation.load(yaml, "players." + playerKey + ".homes." + homeName);
                if (location != null) {
                    loaded.put(key(homeName), location);
                }
            }
            if (!loaded.isEmpty()) {
                this.homes.put(normalized, loaded);
                loadedAny = true;
            }
        }
        return loadedAny;
    }

    private static String key(final String value) {
        return value.toLowerCase(Locale.ROOT);
    }
}
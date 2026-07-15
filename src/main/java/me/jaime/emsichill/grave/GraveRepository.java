package me.jaime.emsichill.grave;

import java.io.File;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import me.jaime.emsichill.Main;

/** Guarda tumbas y preferencias de pérdida de inventario sin mezclar YAML con eventos de juego. */
final class GraveRepository {
    private final Main plugin;
    private final File dataFile;
    private final Map<String, Grave> graves = new ConcurrentHashMap<>();
    private final Map<String, LossMode> playerModes = new ConcurrentHashMap<>();

    GraveRepository(final Main plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "Graves/data.yml");
        this.load();
    }

    Collection<Grave> all() { return this.graves.values(); }
    Grave get(final String id) { return this.graves.get(id); }
    void put(final Grave grave) { this.graves.put(grave.id(), grave); }
    Grave remove(final String id) { return this.graves.remove(id); }
    boolean contains(final String id) { return this.graves.containsKey(id); }
    int size() { return this.graves.size(); }

    LossMode mode(final String playerName, final LossMode fallback) {
        return this.playerModes.getOrDefault(key(playerName), fallback);
    }

    void setMode(final String playerName, final LossMode mode) {
        this.playerModes.put(key(playerName), mode);
    }

    void save() {
        this.plugin.dataStore().saveAsync(this.dataFile, this.snapshot());
    }

    boolean saveNow() {
        return this.plugin.dataStore().saveNow(this.dataFile, this.snapshot());
    }

    private void load() {
        if (!this.dataFile.exists()) {
            return;
        }
        YamlConfiguration yaml = this.plugin.dataStore().load(this.dataFile);
        ConfigurationSection modes = yaml.getConfigurationSection("player-modes");
        if (modes != null) {
            for (String playerName : modes.getKeys(false)) {
                LossMode mode = LossMode.parse(yaml.getString("player-modes." + playerName));
                if (mode != null) {
                    this.playerModes.put(key(playerName), mode);
                }
            }
        }
        ConfigurationSection graveSection = yaml.getConfigurationSection("graves");
        if (graveSection != null) {
            for (String id : graveSection.getKeys(false)) {
                Grave grave = Grave.load(yaml, "graves." + id, id);
                if (grave != null) {
                    this.graves.put(id, grave);
                }
            }
        }
    }

    private YamlConfiguration snapshot() {
        YamlConfiguration yaml = new YamlConfiguration();
        // El snapshot desacopla la escritura asíncrona de cambios posteriores en las colecciones.
        for (Map.Entry<String, LossMode> entry : this.playerModes.entrySet()) {
            yaml.set("player-modes." + entry.getKey(), entry.getValue().name().toLowerCase(Locale.ROOT));
        }
        for (Grave grave : this.graves.values()) {
            grave.save(yaml, "graves." + grave.id());
        }
        return yaml;
    }

    private static String key(final String value) {
        return value.toLowerCase(Locale.ROOT);
    }
}
package me.jaime.emsichill.staff;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import me.jaime.emsichill.Main;

/** Lee y guarda vanish e instantáneas sin contener reglas de moderación. */
final class StaffRepository {
    private final Main plugin;
    private final File dataFile;

    StaffRepository(final Main plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "Staff/data.yml");
    }

    StaffData load() {
        if (!this.dataFile.exists()) return new StaffData(new HashSet<>(), new HashMap<>());
        YamlConfiguration yaml = this.plugin.dataStore().load(this.dataFile);
        var vanished = new HashSet<>(yaml.getStringList("vanished"));
        Map<String, StaffSnapshot> snapshots = new HashMap<>();
        ConfigurationSection section = yaml.getConfigurationSection("staffmode");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                StaffSnapshot snapshot = StaffSnapshot.load(yaml, "staffmode." + key);
                if (snapshot != null) snapshots.put(key.toLowerCase(Locale.ROOT), snapshot);
            }
        }
        return new StaffData(vanished, snapshots);
    }

    void save(final Iterable<String> vanished, final Map<String, StaffSnapshot> snapshots) {
        YamlConfiguration yaml = new YamlConfiguration();
        var names = new ArrayList<String>();
        vanished.forEach(names::add);
        yaml.set("vanished", names);
        for (Map.Entry<String, StaffSnapshot> entry : snapshots.entrySet()) {
            entry.getValue().save(yaml, "staffmode." + entry.getKey());
        }
        this.plugin.dataStore().saveAsync(this.dataFile, yaml);
    }
}

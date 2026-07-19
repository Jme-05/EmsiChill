package me.jaime.emsichill.staff;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import me.jaime.emsichill.Main;

/** Traduce el estado de moderacion a un YAML independiente del resto del modulo de staff. */
final class ModerationRepository {
    private final Main plugin;
    private final File dataFile;

    ModerationRepository(final Main plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "Staff/moderation.yml");
    }

    Map<String, PlayerModeration> load() {
        Map<String, PlayerModeration> records = new LinkedHashMap<>();
        YamlConfiguration yaml = this.plugin.dataStore().load(this.dataFile);
        ConfigurationSection players = yaml.getConfigurationSection("players");
        if (players == null) return records;
        for (String key : players.getKeys(false)) {
            String path = "players." + key;
            String name = yaml.getString(path + ".name");
            if (name == null || name.isBlank()) continue;
            MuteRecord mute = this.loadMute(yaml, path + ".active-mute");
            List<ModerationEntry> history = this.loadHistory(yaml, path + ".history");
            records.put(name.toLowerCase(Locale.ROOT), new PlayerModeration(name, mute, history));
        }
        return records;
    }

    void save(final Map<String, PlayerModeration> records) {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<String, PlayerModeration> item : records.entrySet()) {
            String path = "players." + item.getKey();
            PlayerModeration record = item.getValue();
            yaml.set(path + ".name", record.name());
            this.saveMute(yaml, path + ".active-mute", record.mute());
            for (int index = 0; index < record.history().size(); index++) {
                ModerationEntry entry = record.history().get(index);
                String entryPath = path + ".history." + index;
                yaml.set(entryPath + ".created-at", entry.createdAt());
                yaml.set(entryPath + ".action", entry.action().name());
                yaml.set(entryPath + ".moderator", entry.moderator());
                yaml.set(entryPath + ".reason", entry.reason());
            }
        }
        this.plugin.dataStore().saveAsync(this.dataFile, yaml);
    }

    private MuteRecord loadMute(final YamlConfiguration yaml, final String path) {
        if (!yaml.isConfigurationSection(path)) return null;
        String moderator = yaml.getString(path + ".moderator", "-");
        String reason = yaml.getString(path + ".reason", "-");
        return new MuteRecord(yaml.getLong(path + ".created-at"), yaml.getLong(path + ".expires-at"),
            moderator, reason);
    }

    private List<ModerationEntry> loadHistory(final YamlConfiguration yaml, final String path) {
        List<ModerationEntry> history = new ArrayList<>();
        ConfigurationSection section = yaml.getConfigurationSection(path);
        if (section == null) return history;
        List<String> keys = new ArrayList<>(section.getKeys(false));
        keys.sort((left, right) -> Integer.compare(this.number(left), this.number(right)));
        for (String key : keys) {
            String entryPath = path + "." + key;
            try {
                ModerationAction action = ModerationAction.valueOf(
                    yaml.getString(entryPath + ".action", "WARNING").toUpperCase(Locale.ROOT));
                history.add(new ModerationEntry(yaml.getLong(entryPath + ".created-at"), action,
                    yaml.getString(entryPath + ".moderator", "-"),
                    yaml.getString(entryPath + ".reason", "-")));
            } catch (IllegalArgumentException ignored) {
                // Entradas desconocidas se omiten para conservar el resto del historial.
            }
        }
        return history;
    }

    private void saveMute(final YamlConfiguration yaml, final String path, final MuteRecord mute) {
        if (mute == null) return;
        yaml.set(path + ".created-at", mute.createdAt());
        yaml.set(path + ".expires-at", mute.expiresAt());
        yaml.set(path + ".moderator", mute.moderator());
        yaml.set(path + ".reason", mute.reason());
    }

    private int number(final String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return Integer.MAX_VALUE;
        }
    }
}

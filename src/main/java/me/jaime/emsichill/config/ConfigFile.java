package me.jaime.emsichill.config;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import org.bukkit.configuration.file.YamlConfiguration;

import me.jaime.emsichill.Main;

/** Carga, recarga y guarda un archivo de configuración incluido en los recursos del plugin. */
public final class ConfigFile {
    private final Main plugin;
    private final String resourcePath;
    private final File file;
    private YamlConfiguration configuration;

    public ConfigFile(final Main plugin, final String resourcePath) {
        this.plugin = plugin;
        this.resourcePath = resourcePath;
        this.file = new File(plugin.getDataFolder(), resourcePath);
        this.reload();
    }

    public void reload() {
        if (!this.file.exists()) {
            this.plugin.saveResource(this.resourcePath, false);
        }
        this.configuration = this.plugin.dataStore().load(this.file);

        // Los valores nuevos se incorporan sin reemplazar ajustes existentes del servidor.
        try (InputStream stream = this.plugin.getResource(this.resourcePath)) {
            if (stream != null) {
                YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(stream, StandardCharsets.UTF_8)
                );
                boolean updated = false;
                for (String key : defaults.getKeys(true)) {
                    if (!defaults.isConfigurationSection(key) && !this.configuration.contains(key, true)) {
                        this.configuration.set(key, defaults.get(key));
                        updated = true;
                    }
                }
                this.configuration.setDefaults(defaults);
                if (updated) this.save();
            }
        } catch (IOException exception) {
            this.plugin.getLogger().warning("No se pudieron cargar los valores por defecto de " + this.resourcePath);
        }
    }

    public YamlConfiguration yaml() {
        return this.configuration;
    }

    public File file() {
        return this.file;
    }

    public boolean save() {
        return this.plugin.dataStore().saveNow(this.file, this.configuration);
    }
}
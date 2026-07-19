package me.jaime.emsichill.documentation;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.configuration.file.YamlConfiguration;

import me.jaime.emsichill.Main;

/** Catálogo inmutable construido desde la sección documentation de plugin.yml. */
public final class CommandDocumentation {
    private final Map<String, String> sectionTitles;
    private final List<CommandDoc> entries;

    private CommandDocumentation(final Map<String, String> sectionTitles, final List<CommandDoc> entries) {
        this.sectionTitles = Collections.unmodifiableMap(new LinkedHashMap<>(sectionTitles));
        this.entries = List.copyOf(entries);
    }

    public static CommandDocumentation load(final Main plugin) {
        try (InputStream stream = plugin.getResource("plugin.yml")) {
            if (stream == null) return empty();
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(
                new InputStreamReader(stream, StandardCharsets.UTF_8));
            return from(yaml);
        } catch (java.io.IOException exception) {
            plugin.getLogger().warning("No se pudo cargar la documentación de comandos.");
            return empty();
        }
    }

    public static CommandDocumentation from(final YamlConfiguration yaml) {
        Map<String, String> titles = new LinkedHashMap<>();
        var section = yaml.getConfigurationSection("documentation.sections");
        if (section != null) {
            for (String key : section.getKeys(false)) titles.put(key, section.getString(key, key));
        }

        List<CommandDoc> entries = new ArrayList<>();
        for (Map<?, ?> raw : yaml.getMapList("documentation.entries")) {
            String sectionName = value(raw, "section");
            String category = value(raw, "category");
            String command = value(raw, "command");
            String description = value(raw, "description");
            if (!sectionName.isBlank() && !category.isBlank() && !command.isBlank() && !description.isBlank()) {
                entries.add(new CommandDoc(sectionName, category, command, description));
            }
        }
        return new CommandDocumentation(titles, entries);
    }

    public Map<String, String> sectionTitles() {
        return this.sectionTitles;
    }

    public List<CommandDoc> entriesForSection(final String section) {
        return this.entries.stream().filter(entry -> entry.section().equalsIgnoreCase(section)).toList();
    }

    public List<CommandDoc> entriesForCategory(final String category) {
        return this.entries.stream().filter(entry -> entry.category().equalsIgnoreCase(category)).toList();
    }

    public boolean hasCategory(final String category) {
        return this.entries.stream().anyMatch(entry -> entry.category().equalsIgnoreCase(category));
    }

    private static String value(final Map<?, ?> map, final String key) {
        Object value = map.get(key);
        return value == null ? "" : value.toString().trim();
    }

    private static CommandDocumentation empty() {
        return new CommandDocumentation(Collections.emptyMap(), Collections.emptyList());
    }
}

package me.jaime.emsichill.documentation;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import org.bukkit.configuration.file.YamlConfiguration;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/** Herramienta de compilación que mantiene README y permisos sincronizados con plugin.yml. */
public final class ReadmeGenerator {
    static final String START = "<!-- EMSICHILL_COMMANDS_START -->";
    static final String END = "<!-- EMSICHILL_COMMANDS_END -->";

    private ReadmeGenerator() {
    }

    public static void main(final String[] args) throws IOException {
        if (args.length != 3) throw new IllegalArgumentException("Uso: <plugin.yml> <README.md> <PERMISSIONS.md>");
        generate(Path.of(args[0]), Path.of(args[1]), Path.of(args[2]));
    }

    static void generate(final Path pluginYml, final Path readme, final Path permissions) throws IOException {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new File(pluginYml.toString()));
        CommandDocumentation documentation = CommandDocumentation.from(yaml);
        updateReadme(readme, commandMarkdown(documentation));
        writeIfChanged(permissions, permissionMarkdown(loadStructured(pluginYml)));
    }

    private static String commandMarkdown(final CommandDocumentation documentation) {
        StringBuilder markdown = new StringBuilder();
        for (Map.Entry<String, String> section : documentation.sectionTitles().entrySet()) {
            var entries = documentation.entriesForSection(section.getKey());
            if (entries.isEmpty()) continue;
            markdown.append("## ").append(section.getValue()).append("\n\n")
                .append("| Comando | Descripción |\n|---|---|\n");
            for (CommandDoc entry : entries) {
                markdown.append("| `").append(entry.command().replace("|", "\\|"))
                    .append("` | ").append(entry.description().replace("|", "\\|"))
                    .append(" |\n");
            }
            markdown.append('\n');
        }
        return markdown.toString().stripTrailing();
    }

    private static String permissionMarkdown(final Map<String, Object> yaml) {
        StringBuilder markdown = new StringBuilder("# Permisos de EmsiChill\n\n")
            .append("Este archivo se genera automáticamente desde `plugin.yml`.\n\n")
            .append("| Permiso | Predeterminado | Descripción |\n|---|---|---|\n");
        Map<String, Object> permissions = map(yaml.get("permissions"));
        for (String key : permissions.keySet().stream().sorted().toList()) {
            Map<String, Object> definition = map(permissions.get(key));
            String value = String.valueOf(definition.getOrDefault("default", false));
            String description = String.valueOf(definition.getOrDefault("description", "Sin descripción."));
            markdown.append("| `").append(key).append("` | `").append(value)
                .append("` | ").append(description.replace("|", "\\|"))
                .append(" |\n");
        }
        return markdown.toString();
    }

    static Map<String, Object> loadStructured(final Path path) throws IOException {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        Yaml parser = new Yaml(new SafeConstructor(options));
        try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return map(parser.load(reader));
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(final Object value) {
        return value instanceof Map<?, ?> raw ? (Map<String, Object>) raw : new LinkedHashMap<>();
    }

    private static void updateReadme(final Path readme, final String generated) throws IOException {
        String contents = Files.readString(readme, StandardCharsets.UTF_8);
        int start = contents.indexOf(START);
        int end = contents.indexOf(END);
        if (start < 0 || end < start) throw new IOException("README.md no contiene los marcadores de documentación");
        String replacement = START + "\n\n" + generated + "\n\n" + END;
        String updated = contents.substring(0, start) + replacement + contents.substring(end + END.length());
        writeIfChanged(readme, updated);
    }

    private static void writeIfChanged(final Path path, final String contents) throws IOException {
        String normalized = contents.replace("\r\n", "\n") + (contents.endsWith("\n") ? "" : "\n");
        if (Files.exists(path) && Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n")
            .equals(normalized)) return;
        if (path.getParent() != null) Files.createDirectories(path.getParent());
        Files.writeString(path, normalized, StandardCharsets.UTF_8);
    }
}

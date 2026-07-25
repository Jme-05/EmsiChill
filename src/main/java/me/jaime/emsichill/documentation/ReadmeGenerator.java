package me.jaime.emsichill.documentation;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.bukkit.configuration.file.YamlConfiguration;

/** Herramienta de compilación que mantiene los comandos del README sincronizados con plugin.yml. */
public final class ReadmeGenerator {
    static final String START = "<!-- EMSICHILL_COMMANDS_START -->";
    static final String END = "<!-- EMSICHILL_COMMANDS_END -->";

    private ReadmeGenerator() {
    }

    public static void main(final String[] args) throws IOException {
        if (args.length != 2) throw new IllegalArgumentException("Uso: <plugin.yml> <README.md>");
        generate(Path.of(args[0]), Path.of(args[1]));
    }

    static void generate(final Path pluginYml, final Path readme) throws IOException {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new File(pluginYml.toString()));
        CommandDocumentation documentation = CommandDocumentation.from(yaml);
        updateReadme(readme, commandMarkdown(documentation));
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

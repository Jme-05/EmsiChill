package me.jaime.emsichill.documentation;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.PluginDescriptionFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReadmeGeneratorTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void generatesCommandsAndPermissionsFromPluginYml() throws Exception {
        Path pluginYml = Path.of("src/main/resources/plugin.yml");
        Path readme = this.temporaryDirectory.resolve("README.md");
        Path permissions = this.temporaryDirectory.resolve("PERMISSIONS.md");
        Files.writeString(readme, "# Test\n\n" + ReadmeGenerator.START + "\nold\n"
            + ReadmeGenerator.END + "\n", StandardCharsets.UTF_8);

        ReadmeGenerator.generate(pluginYml, readme, permissions);

        String generatedReadme = Files.readString(readme, StandardCharsets.UTF_8);
        String generatedPermissions = Files.readString(permissions, StandardCharsets.UTF_8);
        assertTrue(generatedReadme.contains("/invsee <jugador>"));
        assertTrue(generatedReadme.contains("/slay <jugador>"));
        assertTrue(generatedReadme.contains("/emsichill update check"));
        assertTrue(generatedPermissions.contains("emsichill.invsee.modify"));
        assertTrue(generatedPermissions.contains("emsichill.admin.update"));
    }

    @Test
    void documentsEveryRegisteredCommandAndPermission() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(
            Path.of("src/main/resources/plugin.yml").toFile());
        List<String> documented = yaml.getMapList("documentation.entries").stream()
            .map(entry -> String.valueOf(entry.get("command")))
            .toList();

        for (String command : yaml.getConfigurationSection("commands").getKeys(false)) {
            assertTrue(documented.stream().anyMatch(usage -> usage.startsWith("/" + command)),
                () -> "Falta documentar /" + command);
        }
        try {
            var structured = ReadmeGenerator.loadStructured(Path.of("src/main/resources/plugin.yml"));
            var permissions = (java.util.Map<?, ?>) structured.get("permissions");
            for (var entry : permissions.entrySet()) {
                var definition = (java.util.Map<?, ?>) entry.getValue();
                assertTrue(definition.get("description") instanceof String,
                    () -> "Falta describir " + entry.getKey());
            }
        } catch (java.io.IOException exception) {
            throw new AssertionError(exception);
        }
    }

    @Test
    void filteredPluginDescriptorRemainsValidForPaper() throws Exception {
        try (var reader = Files.newBufferedReader(Path.of("target/classes/plugin.yml"), StandardCharsets.UTF_8)) {
            PluginDescriptionFile description = new PluginDescriptionFile(reader);
            assertTrue(description.getCommands().containsKey("invsee"));
            assertTrue(description.getCommands().containsKey("enderchestsee"));
            assertTrue(description.getCommands().containsKey("freeze"));
            assertTrue(description.getCommands().containsKey("slay"));
        }
    }
}

package me.jaime.emsichill.maintenance;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import me.jaime.emsichill.Main;

/** Ejecuta diagnósticos y respaldos administrativos sin depender de los comandos que los invocan. */
public final class MaintenanceService {
    private static final DateTimeFormatter BACKUP_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private final Main plugin;

    public MaintenanceService(final Main plugin) {
        this.plugin = plugin;
    }

    public File createBackup() throws IOException {
        if (!this.plugin.dataStore().flush()) throw new IOException("la cola de guardado no terminó");
        File backupFolder = new File(this.plugin.getDataFolder(), "backups");
        Files.createDirectories(backupFolder.toPath());
        File destination = new File(backupFolder, "EmsiChill-" + BACKUP_TIME.format(LocalDateTime.now()) + ".zip");
        Path root = this.plugin.getDataFolder().toPath().toAbsolutePath().normalize();
        try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(destination.toPath())));
             java.util.stream.Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                Path relative = root.relativize(path);
                if (relative.startsWith("backups") || path.getFileName().toString().endsWith(".tmp")) continue;
                zip.putNextEntry(new ZipEntry(relative.toString().replace('\\', '/')));
                Files.copy(path, zip);
                zip.closeEntry();
            }
        }
        return destination;
    }

    public List<String> diagnose() {
        List<String> issues = new ArrayList<>();
        File dataFolder = this.plugin.getDataFolder();
        if (!dataFolder.isDirectory() || !dataFolder.canRead() || !dataFolder.canWrite()) {
            issues.add("La carpeta de datos no permite lectura y escritura.");
            return issues;
        }
        try (java.util.stream.Stream<Path> paths = Files.walk(dataFolder.toPath())) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
                if (fileName.endsWith(".tmp")) issues.add("Archivo temporal pendiente: " + dataFolder.toPath().relativize(path));
                if (fileName.endsWith(".yml")) this.validateYaml(path.toFile(), issues);
            }
        } catch (IOException exception) {
            issues.add("No se pudo recorrer la carpeta de datos: " + exception.getMessage());
        }
        if (this.plugin.dataStore().pendingWrites() > 0) {
            issues.add("Hay " + this.plugin.dataStore().pendingWrites() + " guardados pendientes.");
        }
        return issues;
    }

    private void validateYaml(final File file, final List<String> issues) {
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file);
        } catch (IOException | InvalidConfigurationException exception) {
            issues.add("YAML inválido: " + this.plugin.getDataFolder().toPath().relativize(file.toPath()));
            return;
        }
        this.findMissingWorlds(yaml, yaml, "", issues, file.getName());
    }

    private void findMissingWorlds(final YamlConfiguration root, final ConfigurationSection section,
                                   final String path, final List<String> issues, final String source) {
        for (String key : section.getKeys(false)) {
            String childPath = path.isEmpty() ? key : path + "." + key;
            if (section.isConfigurationSection(key)) {
                this.findMissingWorlds(root, section.getConfigurationSection(key), childPath, issues, source);
            } else if (key.equalsIgnoreCase("world")) {
                String worldName = root.getString(childPath);
                if (worldName != null && Bukkit.getWorld(worldName) == null) {
                    String issue = "Mundo no cargado en " + source + ": " + worldName;
                    if (!issues.contains(issue)) issues.add(issue);
                }
            }
        }
    }
}
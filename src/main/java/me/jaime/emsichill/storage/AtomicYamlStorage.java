package me.jaime.emsichill.storage;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.logging.Logger;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Escribe YAML mediante un archivo temporal y conserva un respaldo válido para evitar corrupción
 * ante una interrupción durante el guardado.
 */
public final class AtomicYamlStorage {
    public static final int CURRENT_SCHEMA_VERSION = 1;

    private AtomicYamlStorage() {
    }

    public static YamlConfiguration load(final File file, final Logger logger) {
        if (!file.exists()) return new YamlConfiguration();
        try {
            return loadExact(file);
        } catch (IOException | InvalidConfigurationException primaryFailure) {
            File backup = backupFile(file);
            if (backup.exists()) {
                try {
                    YamlConfiguration recovered = loadExact(backup);
                    logger.warning("Se recuperó " + file.getName() + " desde su copia de seguridad.");
                    return recovered;
                } catch (IOException | InvalidConfigurationException backupFailure) {
                    primaryFailure.addSuppressed(backupFailure);
                }
            }
            logger.severe("No se pudo cargar " + file.getAbsolutePath() + ": " + primaryFailure.getMessage());
            return new YamlConfiguration();
        }
    }

    public static String serialize(final YamlConfiguration yaml) {
        if (!yaml.isInt("schema-version")) yaml.set("schema-version", CURRENT_SCHEMA_VERSION);
        return yaml.saveToString();
    }

    public static void write(final File file, final String contents) throws IOException {
        Path target = file.toPath().toAbsolutePath().normalize();
        Path parent = target.getParent();
        if (parent != null) Files.createDirectories(parent);

        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        Files.writeString(temporary, contents, StandardCharsets.UTF_8,
            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);

        if (Files.exists(target)) {
            Files.copy(target, backupFile(file).toPath(), StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.COPY_ATTRIBUTES);
        }
        try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static File backupFile(final File file) {
        return new File(file.getParentFile(), file.getName() + ".backup");
    }

    private static YamlConfiguration loadExact(final File file) throws IOException, InvalidConfigurationException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.load(file);
        return yaml;
    }
}
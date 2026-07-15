package me.jaime.emsichill.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AtomicYamlStorageTest {
    @TempDir
    Path temporaryFolder;

    @Test
    void serializedDataIncludesSchemaVersion() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("player.name", "Jaime");

        String serialized = AtomicYamlStorage.serialize(yaml);

        assertTrue(serialized.contains("schema-version: " + AtomicYamlStorage.CURRENT_SCHEMA_VERSION));
    }

    @Test
    void writeKeepsPreviousValidFileAsBackup() throws Exception {
        File file = this.temporaryFolder.resolve("data.yml").toFile();
        AtomicYamlStorage.write(file, "value: first\n");
        AtomicYamlStorage.write(file, "value: second\n");

        assertEquals("second", AtomicYamlStorage.load(file, Logger.getAnonymousLogger()).getString("value"));
        assertEquals("first", AtomicYamlStorage.load(AtomicYamlStorage.backupFile(file),
            Logger.getAnonymousLogger()).getString("value"));
    }

    @Test
    void invalidPrimaryFileFallsBackToBackup() throws Exception {
        File file = this.temporaryFolder.resolve("accounts.yml").toFile();
        AtomicYamlStorage.write(file, "account: valid\n");
        AtomicYamlStorage.write(file, "account: current\n");
        Files.writeString(file.toPath(), "account: [broken", StandardCharsets.UTF_8);

        YamlConfiguration recovered = AtomicYamlStorage.load(file, Logger.getAnonymousLogger());

        assertEquals("valid", recovered.getString("account"));
    }
}

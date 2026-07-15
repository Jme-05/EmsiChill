package me.jaime.emsichill.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Path;
import java.util.logging.Logger;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DataStoreTest {
    @TempDir
    Path temporaryFolder;

    @Test
    void asynchronousWritesFinishBeforeCloseReturns() {
        File file = this.temporaryFolder.resolve("homes.yml").toFile();
        DataStore store = new DataStore(Logger.getAnonymousLogger());
        YamlConfiguration first = new YamlConfiguration();
        first.set("home", "first");
        YamlConfiguration latest = new YamlConfiguration();
        latest.set("home", "latest");

        store.saveAsync(file, first);
        store.saveAsync(file, latest);
        store.close();

        assertEquals("latest", AtomicYamlStorage.load(file, Logger.getAnonymousLogger()).getString("home"));
    }

    @Test
    void flushWaitsForQueuedAppends() throws Exception {
        File file = this.temporaryFolder.resolve("audit.log").toFile();
        try (DataStore store = new DataStore(Logger.getAnonymousLogger())) {
            store.appendAsync(file, "one\n");
            store.appendAsync(file, "two\n");
            assertTrue(store.flush());
        }

        assertEquals("one\ntwo\n", java.nio.file.Files.readString(file.toPath()).replace("\r\n", "\n"));
    }

    @Test
    void immediateSaveAlwaysWinsOverAnEarlierQueuedSave() {
        File file = this.temporaryFolder.resolve("users.yml").toFile();
        try (DataStore store = new DataStore(Logger.getAnonymousLogger())) {
            YamlConfiguration queued = new YamlConfiguration();
            queued.set("state", "old");
            YamlConfiguration immediate = new YamlConfiguration();
            immediate.set("state", "current");

            store.saveAsync(file, queued);
            assertTrue(store.saveNow(file, immediate));
        }

        assertEquals("current", AtomicYamlStorage.load(file, Logger.getAnonymousLogger()).getString("state"));
    }
}

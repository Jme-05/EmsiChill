package me.jaime.emsichill.update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PaperServerVersionTest {
    @Test
    void readsPaperBuildFromModernVersionString() {
        PaperServerVersion version = PaperServerVersion.detect(
            "26.2",
            "This server is running Paper version 26.2-84-main@abc123 (2026-07-26T18:11:33Z)");

        assertEquals("26.2", version.minecraftVersion());
        assertTrue(version.build().isPresent());
        assertEquals(84, version.build().getAsInt());
        assertEquals("26.2 build #84", version.label());
    }

    @Test
    void fallsBackToMcMarkerWhenBukkitVersionIsMissing() {
        PaperServerVersion version = PaperServerVersion.detect("", "git-Paper-84 (MC: 26.2)");

        assertEquals("26.2", version.minecraftVersion());
        assertEquals(84, version.build().orElseThrow());
    }
}

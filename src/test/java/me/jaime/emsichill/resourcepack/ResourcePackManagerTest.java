package me.jaime.emsichill.resourcepack;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class ResourcePackManagerTest {
    @Test
    void parsesValidPackDefinition() {
        ResourcePackManager.PackDefinition pack = ResourcePackManager.parse(Map.of(
            "id", "main",
            "name", "Main Pack",
            "url", "https://example.com/pack.zip",
            "sha1", "a".repeat(40),
            "required", true,
            "prompt", "Required pack"
        ));

        assertNotNull(pack);
        assertEquals("Main Pack", pack.name());
        assertEquals("a".repeat(40), pack.sha1Hex());
        assertTrue(pack.required());
        assertEquals("Required pack", pack.prompt());
        assertArrayEquals(new byte[] {(byte) 0xaa, (byte) 0xaa}, new byte[] {pack.sha1()[0], pack.sha1()[1]});
    }

    @Test
    void rejectsInvalidSha1AndUrl() {
        assertNull(ResourcePackManager.parse(Map.of(
            "id", "bad",
            "url", "https://example.com/pack.zip",
            "sha1", "not-a-sha1"
        )));
        assertNull(ResourcePackManager.parse(Map.of(
            "id", "bad",
            "url", "file:///pack.zip",
            "sha1", "a".repeat(40)
        )));
    }

    @Test
    void buildsStableUuidFromTextId() {
        UUID first = ResourcePackManager.packId("main");
        UUID second = ResourcePackManager.packId("main");

        assertEquals(first, second);
        assertEquals(UUID.fromString("01234567-89ab-cdef-0123-456789abcdef"),
            ResourcePackManager.packId("01234567-89ab-cdef-0123-456789abcdef"));
    }

    @Test
    void validatesSha1Format() {
        assertTrue(ResourcePackManager.validSha1("A".repeat(40)));
        assertFalse(ResourcePackManager.validSha1("a".repeat(39)));
        assertFalse(ResourcePackManager.validSha1("g".repeat(40)));
        assertFalse(ResourcePackManager.validSha1("0".repeat(40)));
    }
}

package me.jaime.emsichill.skin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SkinSettingsTest {
    @Test
    void acceptsMinecraftUsernames() {
        assertTrue(SkinSettings.isValidMinecraftName("Notch"));
        assertTrue(SkinSettings.isValidMinecraftName("jme_05"));
        assertTrue(SkinSettings.isValidMinecraftName("abc"));
    }

    @Test
    void rejectsInvalidMinecraftUsernames() {
        assertFalse(SkinSettings.isValidMinecraftName(null));
        assertFalse(SkinSettings.isValidMinecraftName("ab"));
        assertFalse(SkinSettings.isValidMinecraftName("nombre con espacio"));
        assertFalse(SkinSettings.isValidMinecraftName("nombre-demasiado-largo"));
    }
}

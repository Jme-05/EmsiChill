package me.jaime.emsichill.staff;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class FreezeServiceTest {
    @Test
    void togglesAndReleasesTemporaryState() {
        FreezeService service = new FreezeService();
        UUID player = UUID.randomUUID();

        assertTrue(service.toggle(player));
        assertTrue(service.isFrozen(player));
        assertFalse(service.toggle(player));
        assertFalse(service.isFrozen(player));
    }
}

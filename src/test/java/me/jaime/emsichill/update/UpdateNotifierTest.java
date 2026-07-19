package me.jaime.emsichill.update;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class UpdateNotifierTest {
    @Test
    void checksWhenNoCachedResultExists() {
        assertTrue(UpdateNotifier.shouldCheckOnJoin(0L, 100_000L, 60_000L));
    }

    @Test
    void reusesFreshResultsAndRefreshesOldOnes() {
        assertFalse(UpdateNotifier.shouldCheckOnJoin(70_000L, 100_000L, 60_000L));
        assertTrue(UpdateNotifier.shouldCheckOnJoin(40_000L, 100_000L, 60_000L));
    }
}

package me.jaime.emsichill.update;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class UpdateNoticeTrackerTest {
    @Test
    void announcesEachReleaseOnlyOncePerRecipient() {
        UpdateNoticeTracker tracker = new UpdateNoticeTracker();
        UUID admin = UUID.randomUUID();

        assertTrue(tracker.markConsole("v5.1.0"));
        assertFalse(tracker.markConsole("v5.1.0"));
        assertTrue(tracker.markPlayer("v5.1.0", admin));
        assertFalse(tracker.markPlayer("v5.1.0", admin));
    }

    @Test
    void resetsRecipientsWhenANewerReleaseAppears() {
        UpdateNoticeTracker tracker = new UpdateNoticeTracker();
        UUID admin = UUID.randomUUID();

        tracker.markConsole("v5.1.0");
        tracker.markPlayer("v5.1.0", admin);

        assertTrue(tracker.markConsole("v5.1.1"));
        assertTrue(tracker.markPlayer("v5.1.1", admin));
    }
}

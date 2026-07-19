package me.jaime.emsichill.staff;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class FreezeServiceTest {
    @Test
    void togglesAndReleasesTemporaryState() {
        FakeScheduler scheduler = new FakeScheduler();
        FreezeService service = new FreezeService(scheduler);
        UUID player = UUID.randomUUID();

        assertTrue(service.toggle(player));
        assertTrue(service.isFrozen(player));
        assertFalse(service.toggle(player));
        assertFalse(service.isFrozen(player));
    }

    @Test
    void releasesPlayerWhenTimedFreezeExpires() {
        FakeScheduler scheduler = new FakeScheduler();
        FreezeService service = new FreezeService(scheduler);
        UUID player = UUID.randomUUID();
        int[] expirations = {0};

        service.freezeFor(player, 30, () -> expirations[0]++);

        assertTrue(service.isFrozen(player));
        assertEquals(600L, scheduler.tasks.getFirst().delayTicks);
        scheduler.tasks.getFirst().run();
        assertFalse(service.isFrozen(player));
        assertEquals(1, expirations[0]);
    }

    @Test
    void manualReleaseCancelsTimedFreeze() {
        FakeScheduler scheduler = new FakeScheduler();
        FreezeService service = new FreezeService(scheduler);
        UUID player = UUID.randomUUID();
        int[] expirations = {0};

        service.freezeFor(player, 10, () -> expirations[0]++);
        assertTrue(service.release(player));
        scheduler.tasks.getFirst().run();

        assertFalse(service.isFrozen(player));
        assertEquals(0, expirations[0]);
    }

    private static final class FakeScheduler implements FreezeService.Scheduler {
        private final List<FakeTask> tasks = new ArrayList<>();

        @Override
        public FreezeService.ScheduledTask runLater(final Runnable action, final long delayTicks) {
            FakeTask task = new FakeTask(action, delayTicks);
            this.tasks.add(task);
            return task;
        }
    }

    private static final class FakeTask implements FreezeService.ScheduledTask {
        private final Runnable action;
        private final long delayTicks;
        private boolean cancelled;

        private FakeTask(final Runnable action, final long delayTicks) {
            this.action = action;
            this.delayTicks = delayTicks;
        }

        private void run() {
            if (!this.cancelled) this.action.run();
        }

        @Override
        public void cancel() {
            this.cancelled = true;
        }
    }
}

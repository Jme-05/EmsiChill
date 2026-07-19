package me.jaime.emsichill.staff;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import me.jaime.emsichill.Main;

/** Mantiene congelaciones manuales o cronometradas sin persistirlas tras un reinicio. */
public final class FreezeService {
    private final Set<UUID> frozen = new HashSet<>();
    private final Map<UUID, ScheduledTask> expirationTasks = new HashMap<>();
    private final Scheduler scheduler;

    public FreezeService(final Main plugin) {
        this((action, delayTicks) -> {
            BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, action, delayTicks);
            return task::cancel;
        });
    }

    FreezeService(final Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    public boolean toggle(final UUID playerId) {
        if (this.release(playerId)) return false;
        this.frozen.add(playerId);
        return true;
    }

    public void freezeFor(final UUID playerId, final int seconds, final Runnable expirationAction) {
        this.release(playerId);
        this.frozen.add(playerId);
        ScheduledTask task = this.scheduler.runLater(() -> {
            this.expirationTasks.remove(playerId);
            if (this.frozen.remove(playerId)) expirationAction.run();
        }, seconds * 20L);
        this.expirationTasks.put(playerId, task);
    }

    public boolean isFrozen(final UUID playerId) {
        return this.frozen.contains(playerId);
    }

    public boolean release(final UUID playerId) {
        ScheduledTask task = this.expirationTasks.remove(playerId);
        if (task != null) task.cancel();
        return this.frozen.remove(playerId);
    }

    public void clear() {
        this.expirationTasks.values().forEach(ScheduledTask::cancel);
        this.expirationTasks.clear();
        this.frozen.clear();
    }

    @FunctionalInterface
    interface Scheduler {
        ScheduledTask runLater(Runnable action, long delayTicks);
    }

    @FunctionalInterface
    interface ScheduledTask {
        void cancel();
    }
}
